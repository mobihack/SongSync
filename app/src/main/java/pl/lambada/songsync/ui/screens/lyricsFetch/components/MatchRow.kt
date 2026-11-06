package pl.lambada.songsync.ui.screens.lyricsFetch.components

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
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
    /** Searching, with the provider being tried now. */
    data class Searching(val provider: Providers, val progress: ProviderChain.Progress?) : MatchRowState

    /** The song a provider found, with whether its lyrics are synced. */
    data class Found(val song: SongInfo, val provider: Providers, val synced: Boolean?) : MatchRowState
}

/**
 * The song a provider matched, under the local song: its cover and title, the provider (tap to
 * choose another) and whether its length matches the file's. Next result and Edit search sit at
 * its end, so a wrong match is fixed right where it shows.
 */
@Composable
fun MatchRow(
    state: MatchRowState,
    /** The local file's length, to compare against; null when unknown. */
    localDurationMs: Long?,
    /** The local file's artist: the match's artist is shown only when it differs. */
    localArtist: String?,
    onProviderClick: () -> Unit,
    onNextResult: (() -> Unit)?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = listGroupContainerColor(),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Searching → found is a short crossfade, not a swap in one frame
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) using SizeTransform(clip = false) },
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
                        is MatchRowState.Searching -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                                ProviderLabel(state.provider, onProviderClick)
                                state.progress?.takeIf { it.total > 1 }?.let {
                                    Label("· " + stringResource(R.string.provider_progress, it.index, it.total))
                                }
                            }
                            Text(
                                stringResource(R.string.looking_for_lyrics),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }

                        is MatchRowState.Found -> {
                            val found = state.song.durationMs
                            val mismatch = found != null && localDurationMs != null &&
                                abs(found - localDurationMs) > LengthToleranceMs
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(if (mismatch) MaterialTheme.colorScheme.error else MatchOk, CircleShape)
                                )
                                ProviderLabel(state.provider, onProviderClick)
                                when {
                                    mismatch -> Label(
                                        "· " + stringResource(
                                            R.string.lyrics_length_mismatch,
                                            formatDuration(found!!),
                                            formatDuration(localDurationMs!!),
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    state.synced != null -> Label(
                                        "· " + stringResource(if (state.synced) R.string.lyrics_synced else R.string.lyrics_unsynced)
                                    )
                                }
                            }
                            val title = state.song.songName ?: stringResource(R.string.unknown)
                            val artist = state.song.artistName
                            val showArtist = artist != null && !artist.equals(localArtist, ignoreCase = true)
                            Text(
                                if (showArtist) "$title · $artist" else title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                }

                if (state is MatchRowState.Found) {
                    if (onNextResult != null) WithTooltip(stringResource(R.string.try_again)) {
                        IconButton(onClick = onNextResult) {
                            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.try_again))
                        }
                    }
                    WithTooltip(stringResource(R.string.edit)) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }
                }
            }
        }
    }
}

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
