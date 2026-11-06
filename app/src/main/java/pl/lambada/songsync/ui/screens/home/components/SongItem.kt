package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

/** The cover's corner as a percentage of the cover, the start of the morph to a circle. */
private const val CoverCornerPercent = 12
/** Side inset of the selected row's pill. */
private val SelectedPillInset = 8.dp
private val checkScrim = Color.Black.copy(alpha = 0.45f)
private val CoverSize = 56.dp
/** The cover's corner in the row. */
private const val CoverCornerPx = 20f
/** The lyrics screen cover's corner, as a fraction of its side (its StageCoverShape, 20%). */
private const val LyricsCoverCornerFraction = 0.20f

/**
 * The cover's rounding as it grows into the lyrics screen's cover: [CoverCornerPx] at rest,
 * turning into that cover's proportional corner by the time it's a quarter larger. So it lands
 * with the same rounding as the cover fading in over it, instead of switching at the end; and
 * coming back from the compact header (a little larger than this), it starts from that one's.
 */
private data class GrowingCoverShape(val restPx: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = size.minDimension
        val grown = if (restPx > 0f) ((side - restPx) / (restPx * 0.25f)).coerceIn(0f, 1f) else 0f
        val radius = CoverCornerPx + (side * LyricsCoverCornerFraction - CoverCornerPx) * grown
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius)))
    }
}

/**
 * @param sharedElementsEnabled whether this row takes part in the shared-element transition to
 * the lyrics screen. Only the row being opened (or returned to) should pass true: each
 * sharedBounds modifier costs work on every frame, which adds up across a long list.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
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

    // Selected rows become a rounded secondaryContainer pill and their cover a circle with a
    // check. Drawn behind the row (no layout change), so unselected rows cost nothing extra.
    val selectedBg = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    // Opacity and colour don't overshoot; only the check's scale gets the Expressive bounce.
    // Both are read in layout/draw lambdas, so the row doesn't recompose on every frame.
    val selectionAlphaState = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "songItemSelectionAlpha",
    )
    val selectionAlpha by selectionAlphaState
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "songItemSelectionScale",
    )
    val showCheck by remember { derivedStateOf { selectionAlphaState.value > 0f } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showPath) 100.dp else 80.dp)
            .drawBehind {
                if (selectionAlpha > 0f) {
                    val inset = SelectedPillInset.toPx()
                    drawRoundRect(
                        color = selectedBg,
                        alpha = selectionAlpha,
                        topLeft = Offset(inset, 2.dp.toPx()),
                        size = Size(size.width - inset * 2, size.height - 4.dp.toPx()),
                        cornerRadius = CornerRadius(size.height / 2 * selectionAlpha),
                    )
                }
            }
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
                // The cover's size in the row, where the transition's growth is measured from
                var coverRestPx by remember { mutableFloatStateOf(0f) }
                val growingShape = remember(coverRestPx) { GrowingCoverShape(coverRestPx) }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .onSizeChanged { coverRestPx = it.width.toFloat() },
                    contentAlignment = Alignment.Center,
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
                                overlayClip = growingShape,
                                homeSideCover = true,
                            )
                            .fillMaxSize()
                            .graphicsLayer {
                                // Rounds into a circle as the song is selected
                                shape = if (selectionAlpha == 0f) growingShape
                                else RoundedCornerShape(
                                    percent = (CoverCornerPercent + (50 - CoverCornerPercent) * selectionAlpha)
                                        .toInt().coerceIn(0, 50)
                                )
                                clip = true
                            }
                            .drawWithContent {
                                drawContent()
                                if (selectionAlpha > 0f) {
                                    drawRect(checkScrim, alpha = selectionAlpha)
                                }
                            }
                    )
                    if (showCheck) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    alpha = selectionAlpha
                                    // Starts near full size: a check doesn't grow from a dot
                                    val scale = 0.9f + 0.1f * selectionScale
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                    }
                }
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
                            scaleText = true,
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
                            scaleText = true,
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

/**
 * Shared title/artist text: drawn at its own size and scaled to the moving bounds, start-aligned,
 * so a big title shrinks into a small one instead of reflowing (and re-wrapping) every frame.
 * Used on both ends: here and on the lyrics screen's SongCard.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val SharedTextResizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
    contentScale = ContentScale.FillHeight,
    alignment = Alignment.CenterStart,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedBoundsIf(
    enabled: Boolean,
    key: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    overlayClip: Shape? = null,
    /**
     * A home-screen cover: drawn for the whole transition, both ways, instead of crossfading.
     * The lyrics screen's cover fades in late and out fast (see SongCard), so its own shape
     * doesn't show around this one while the two are different sizes.
     */
    homeSideCover: Boolean = false,
    /** Text: scale the content to the moving bounds instead of reflowing it at every size. */
    scaleText: Boolean = false,
): Modifier {
    if (!enabled) return this
    return with(sharedTransitionScope) {
        val state = rememberSharedContentState(key = key)
        val enter = if (homeSideCover) EnterTransition.None else fadeIn()
        // Fully opaque throughout: stays visible until the shared bounds settle
        val exit = if (homeSideCover) fadeOut(targetAlpha = 1f) else fadeOut()
        val resizeMode = if (scaleText) SharedTextResizeMode else SharedTransitionScope.ResizeMode.RemeasureToBounds
        if (overlayClip != null) {
            this@sharedBoundsIf.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = animatedVisibilityScope,
                enter = enter,
                exit = exit,
                resizeMode = resizeMode,
                clipInOverlayDuringTransition = OverlayClip(overlayClip),
            )
        } else {
            this@sharedBoundsIf.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = animatedVisibilityScope,
                enter = enter,
                exit = exit,
                resizeMode = resizeMode,
            )
        }
    }
}
