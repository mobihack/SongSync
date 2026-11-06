package pl.lambada.songsync.ui.screens.lyricsFetch.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.LocalSong
import pl.lambada.songsync.ui.components.AnimatedText
import pl.lambada.songsync.ui.components.rememberSystemAnimationsEnabled
import kotlin.math.max
import kotlin.math.roundToInt

/** The cover's size while searching, as the screen's centrepiece. */
val StageHeroCoverSize = 176.dp
private val CompactCoverSize = 64.dp
private val HeroTextGap = 20.dp
private val CompactTextGap = 14.dp
/** How far the compact text slides in from. */
private val CompactTextShift = 24.dp

/**
 * The cover's shape at every size: corners in proportion, so the large cover and the compact one
 * (and the home cover morphing into it) agree.
 */
val StageCoverShape = RoundedCornerShape(percent = 20)

/**
 * The local song this screen is for. While the first search runs it is the centrepiece: a large
 * cover with the title and artist under it. Once there's a result ([collapsed]) it settles into a
 * compact row, the cover moving into place beside the title, so the lyrics get the room.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.StageHeader(
    song: LocalSong,
    collapsed: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    disableMarquee: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animate = rememberSystemAnimationsEnabled()
    val progress by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        // No overshoot: the cover lands in its slot instead of bouncing past it
        animationSpec = if (animate) spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium) else snap(),
        label = "stageHeader",
    )
    val artist = song.artists.ifBlank { stringResource(R.string.unknown) }

    Layout(
        modifier = modifier,
        contents = listOf<@Composable () -> Unit>(
            {
                Box(
                    Modifier
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "cover${song.filePath}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            // The home-side cover stays drawn throughout and turns into this shape;
                            // this one shows only near the end, and leaves fast on back
                            enter = fadeIn(tween(durationMillis = 150, delayMillis = 200)),
                            exit = fadeOut(tween(durationMillis = 100)),
                            clipInOverlayDuringTransition = OverlayClip(StageCoverShape),
                        )
                        .clip(StageCoverShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    AsyncImage(
                        model = song.coverUri,
                        imageLoader = context.imageLoader,
                        contentDescription = stringResource(R.string.album_cover),
                        placeholder = painterResource(R.drawable.ic_song),
                        error = painterResource(R.drawable.ic_song),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            {
                // Large title and artist, under the cover
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (collapsed) Modifier.clearAndSetSemantics { } else Modifier,
                ) {
                    Text(
                        song.songName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            {
                // Compact title and artist, beside the cover
                Column(modifier = if (collapsed) Modifier else Modifier.clearAndSetSemantics { }) {
                    AnimatedText(
                        animate = !disableMarquee,
                        text = song.songName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AnimatedText(
                        animate = !disableMarquee,
                        text = song.durationMs?.let { "$artist · ${formatDuration(it)}" } ?: artist,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
        ),
    ) { (coverMeasurables, heroMeasurables, compactMeasurables), constraints ->
        // Read here, in layout: the collapse relayouts each frame but never recomposes
        val p = progress.coerceIn(0f, 1f)
        val width = constraints.maxWidth
        val big = StageHeroCoverSize.roundToPx()
        val small = CompactCoverSize.roundToPx()
        val coverSize = lerp(big, small, p)
        val compactX = small + CompactTextGap.roundToPx()

        val cover = coverMeasurables.first().measure(Constraints.fixed(coverSize, coverSize))
        val hero = heroMeasurables.first().measure(Constraints(maxWidth = width))
        val compact = compactMeasurables.first().measure(Constraints(maxWidth = (width - compactX).coerceAtLeast(0)))

        val heroHeight = big + HeroTextGap.roundToPx() + hero.height
        val compactHeight = max(small, compact.height)
        layout(width, lerp(heroHeight, compactHeight, p)) {
            cover.place(x = ((width - coverSize) / 2f * (1f - p)).roundToInt(), y = 0)
            // The large text follows the cover's bottom edge and fades out early
            hero.placeWithLayer(x = (width - hero.width) / 2, y = coverSize + HeroTextGap.roundToPx()) {
                alpha = (1f - p * 2f).coerceIn(0f, 1f)
            }
            // The compact text fades in late, sliding in beside the cover
            val shift = CompactTextShift.toPx() * (1f - p)
            compact.placeWithLayer(x = compactX, y = (compactHeight - compact.height) / 2) {
                alpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
                translationX = shift
            }
        }
    }
}

private fun lerp(start: Int, stop: Int, fraction: Float): Int =
    (start + (stop - start) * fraction).roundToInt()

/** "3:41" for a length in milliseconds. */
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
