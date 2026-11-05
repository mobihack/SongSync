package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import pl.lambada.songsync.R
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.ui.components.AnimatedText

private val CoverShape = RoundedCornerShape(20f)
private val CoverSize = 56.dp

/**
 * @param sharedElementsEnabled whether this row takes part in the shared-element transition to
 * the lyrics screen. Only the row being opened (or returned to) should pass true: each
 * sharedBounds modifier costs work on every frame, which adds up across a long list.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SongItem(
    filePath: String,
    selected: Boolean,
    quickSelect: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onNavigateToSongRequest: () -> Unit,
    song: Song,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedElementsEnabled: Boolean,
    disableMarquee: Boolean = true,
    showPath: Boolean,
) {
    val context = LocalContext.current
    val coverPx = with(LocalDensity.current) { CoverSize.roundToPx() }
    // Decode album art at row size; full-size embedded art is often 1000px+ per image.
    val coverRequest = remember(song.imgUri, coverPx) {
        ImageRequest.Builder(context)
            .data(song.imgUri)
            .size(coverPx)
            .build()
    }
    val songName = song.title ?: stringResource(id = R.string.unknown)
    val artists = song.artist ?: stringResource(id = R.string.unknown)

    val targetBg = if (selected) MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface
    // Resolve against the target colour: mid-fade colours don't match a scheme colour, so
    // contentColorFor would fall back to the ambient content colour for the duration of the fade.
    val contentColor = MaterialTheme.colorScheme.contentColorFor(targetBg)
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 120),
        label = "songItemBackground"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showPath) 100.dp else 80.dp)
            .background(bgColor)
            .combinedClickable(
                onClick = {
                    if (quickSelect)
                        onSelectionChanged(!selected)
                    else
                        onNavigateToSongRequest()
                },
                onLongClick = { onSelectionChanged(!selected) }
            )
            .padding(vertical = 12.dp, horizontal = 24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxHeight(if (showPath) 0.7f else 1f),
            ) {
                AsyncImage(
                    model = coverRequest,
                    imageLoader = context.imageLoader,
                    contentDescription = stringResource(id = R.string.album_cover),
                    placeholder = painterResource(R.drawable.ic_song),
                    error = painterResource(R.drawable.ic_song),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .sharedBoundsIf(
                            enabled = sharedElementsEnabled,
                            key = "cover$filePath",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            overlayClip = CoverShape,
                        )
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(CoverShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    AnimatedText(
                        animate = !disableMarquee,
                        text = songName,
                        fontSize = 16.sp,
                        color = contentColor,
                        modifier = Modifier.sharedBoundsIf(
                            enabled = sharedElementsEnabled,
                            key = "title$filePath",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    )
                    AnimatedText(
                        animate = !disableMarquee,
                        text = artists,
                        fontSize = 14.sp,
                        color = contentColor,
                        modifier = Modifier.sharedBoundsIf(
                            enabled = sharedElementsEnabled,
                            key = "artist$filePath",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    )
                }
            }
            if (showPath) {
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedText(
                    animate = !disableMarquee,
                    text = filePath.replace(".nowplaying", ""),
                    fontSize = 12.sp,
                    color = contentColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedBoundsIf(
    enabled: Boolean,
    key: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    overlayClip: Shape? = null,
): Modifier {
    if (!enabled) return this
    return with(sharedTransitionScope) {
        val state = rememberSharedContentState(key = key)
        if (overlayClip != null) {
            this@sharedBoundsIf.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = animatedVisibilityScope,
                clipInOverlayDuringTransition = OverlayClip(overlayClip),
            )
        } else {
            this@sharedBoundsIf.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}
