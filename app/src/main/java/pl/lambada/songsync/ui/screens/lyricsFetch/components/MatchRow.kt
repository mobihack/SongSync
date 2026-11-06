package pl.lambada.songsync.ui.screens.lyricsFetch.components

import pl.lambada.songsync.util.ui.EaseOutStrong
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import pl.lambada.songsync.ui.components.AnimatedText
import pl.lambada.songsync.ui.components.WithTooltip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import pl.lambada.songsync.R
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.ui.components.listGroupContainerColor
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.ui.tickerTransform
import kotlin.math.abs

private val MatchCoverSize = 44.dp
private val MatchCoverShape = RoundedCornerShape(percent = 25)
/** A found song this close to the file's length counts as the same recording. */
private const val LengthToleranceMs = 3_000L
/** The "length matches" dot; the scheme has no success colour. */
private val MatchOk = Color(0xFF6DD58C)

/** What the match row shows. */
sealed interface MatchRowState {
    /** The provider being tried, or the one that answered. */
    val provider: Providers

    /** Searching for [query] (title and artist), with the provider being tried now. */
    data class Searching(val query: String, override val provider: Providers, val progress: ProviderChain.Progress?) : MatchRowState

    /** The song a provider found, with whether its lyrics are synced. */
    data class Found(val song: SongInfo, override val provider: Providers, val synced: Boolean?) : MatchRowState
}

/**
 * The song a provider matched, under the local song: a card with its cover, title and artist, and
 * Next result at its end, so a wrong match is fixed right where it shows. Under the card, a
 * caption says where it came from (tap the provider to choose another) and whether it fits the
 * file: synced or not, or a warning when its length differs from the file's.
 */
@Composable
fun MatchRow(
    state: MatchRowState,
    /** The local file's length, to compare against; null when unknown. */
    localDurationMs: Long?,
    /** The local file's artist; used when the match has none. */
    localArtist: String?,
    onProviderClick: () -> Unit,
    onNextResult: (() -> Unit)?,
    /** Scroll the title when it doesn't fit, unless marquee is turned off in settings. */
    marquee: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = listGroupContainerColor(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Searching → found is a short crossfade, not a swap in one frame
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { matchFadeThrough() },
                label = "matchRow",
            ) { state ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Box(
                        Modifier
                            .size(MatchCoverSize)
                            .clip(MatchCoverShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        if (state is MatchRowState.Found) AsyncImage(
                            model = state.song.albumCoverLink,
                            imageLoader = context.imageLoader,
                            contentDescription = stringResource(R.string.album_cover),
                            placeholder = painterResource(R.drawable.ic_song),
                            error = painterResource(R.drawable.ic_song),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(MatchCoverSize),
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        when (state) {
                            // Two lines, like a result, so the card keeps its height when one lands
                            is MatchRowState.Searching -> {
                                AnimatedText(
                                    animate = false,
                                    text = stringResource(R.string.looking_for_lyrics),
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AnimatedText(
                                    animate = false,
                                    text = state.query,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }

                            is MatchRowState.Found -> {
                                AnimatedText(
                                    animate = marquee,
                                    text = state.song.songName ?: stringResource(R.string.unknown),
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                )
                                AnimatedText(
                                    animate = marquee,
                                    text = state.song.artistName ?: localArtist ?: stringResource(R.string.unknown),
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }

                    if (state is MatchRowState.Found && onNextResult != null) {
                        WithTooltip(stringResource(R.string.try_again)) {
                            IconButton(onClick = onNextResult) {
                                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.try_again))
                            }
                        }
                    }
                }
            }
        }

        // Where the match came from, and whether it fits the file. Searching → found fades
        // through, so the spinner and the dot never show at once
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { matchFadeThrough() },
            label = "matchStatus",
            modifier = Modifier.fillMaxWidth(),
        ) { state ->
            // Where on the left, how it fits on the right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    when (state) {
                        is MatchRowState.Searching ->
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        is MatchRowState.Found -> Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (lengthMismatch(state, localDurationMs)) MaterialTheme.colorScheme.error else MatchOk,
                                    CircleShape,
                                )
                        )
                    }
                    ProviderLabel(state.provider, onProviderClick)
                }
                when (state) {
                    is MatchRowState.Searching -> state.progress?.takeIf { it.total > 1 }?.let {
                        Label(stringResource(R.string.provider_progress, it.index, it.total))
                    }
                    is MatchRowState.Found -> when {
                        lengthMismatch(state, localDurationMs) -> Label(
                            stringResource(
                                R.string.lyrics_length_mismatch,
                                formatDuration(state.song.durationMs!!),
                                formatDuration(localDurationMs!!),
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.synced != null -> Label(
                            stringResource(if (state.synced) R.string.lyrics_synced else R.string.lyrics_unsynced)
                        )
                    }
                }
            }
        }

        // Sets the match apart from the lyrics under it
        HorizontalDivider(
            // One physical pixel, softened: a hairline, not a rule
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** The match's length is known and differs from the file's by more than the tolerance. */
private fun lengthMismatch(state: MatchRowState.Found, localDurationMs: Long?): Boolean {
    val found = state.song.durationMs ?: return false
    return localDurationMs != null && abs(found - localDurationMs) > LengthToleranceMs
}

/** Out quickly, then in: the two states never show at once. */
private fun AnimatedContentTransitionScope<*>.matchFadeThrough(): ContentTransform =
    fadeIn(tween(150, delayMillis = 60, easing = EaseOutStrong)) togetherWith
        fadeOut(tween(90, easing = EaseOutStrong)) using SizeTransform(clip = false)

/** The provider's name; ticks over as the fallback moves on, and opens the provider choice. */
@Composable
private fun ProviderLabel(provider: Providers, onClick: () -> Unit) {
    AnimatedContent(
        targetState = provider,
        transitionSpec = { tickerTransform() },
        label = "matchProvider",
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    ) {
        Label(it.displayName)
    }
}

@Composable
private fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
