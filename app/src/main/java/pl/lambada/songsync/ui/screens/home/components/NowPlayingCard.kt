package pl.lambada.songsync.ui.screens.home.components

import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.isActive
import pl.lambada.songsync.R

private val CardCorner = 32.dp
private val CardShape = RoundedCornerShape(CardCorner)
private val MinCardHeight = 200.dp
private val MaxCardHeight = 300.dp
/** Below this card height the title steps down a size, so it never runs into the button. */
private val CompactCardHeight = 230.dp
private const val CoverFraction = 0.74f
private const val SpinMillis = 24_000
/** Song change: how far the details slide, and for how long. */
private val TrackShift = 32.dp
private const val TrackOutMillis = 140
private const val TrackInMillis = 260
private val TrackEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
/** Cover art swap: a shorter slide than the text (it is clipped to the cookie) and a blur. */
private val ArtShift = 24.dp
private val ArtBlur = 12.dp

private data class Track(val title: String, val artist: String, val filePath: String)
/** How far the cover spills past the card's end and bottom edges. */
private val CoverBleedX = 34.dp
private val CoverBleedY = 30.dp
/**
 * The cover on the lyrics screen's song card ([pl.lambada.songsync.ui.components.SongCard]):
 * 72dp, its start corners rounded by 30px and its end corners square.
 */
private val LyricsCoverSize = 72.dp
private const val LyricsCoverCornerPx = 30f

/** Cookie → the lyrics card's cover shape, in a 0..1 box. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun coverMorph(cornerFraction: Float): Morph {
    val round = CornerRounding(cornerFraction)
    return Morph(
        MaterialShapes.Cookie9Sided.normalized(),
        RoundedPolygon.rectangle(
            width = 1f, height = 1f, centerX = 0.5f, centerY = 0.5f,
            // Vertex order: bottom-end, bottom-start, top-start, top-end
            perVertexRounding = listOf(CornerRounding.Unrounded, round, round, CornerRounding.Unrounded),
        ),
    )
}

/**
 * The cover's clip, at rest and through the shared-element transition to the lyrics screen.
 * Progress comes from the cover's own size as it shrinks from [restPx] to [endPx]: the cookie
 * morphs into the lyrics card's rounded square, its turn unwinds, and the crop that matches the
 * card's edges opens up. Without that crop, the parts hidden past the card's edge would pop
 * into view the moment the cover leaves the card.
 */
private data class CoverClipShape(
    val morph: Morph,
    val angle: Float,
    val restPx: Float,
    val endPx: Float,
    val bleedXPx: Float,
    val bleedYPx: Float,
    /** The card's corner, which trims the cover's bottom-end at rest. */
    val cardCornerPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val progress = ((restPx - size.width) / (restPx - endPx)).coerceIn(0f, 1f)
        val path = android.graphics.Path()
        morph.toPath(progress, path)
        // 9 lobes: any turn looks the same as one within ±20°, so unwind the short way
        val turn = ((angle % 40f + 60f) % 40f - 20f) * (1f - progress)
        path.transform(android.graphics.Matrix().apply {
            setScale(size.width, size.height)
            postRotate(turn, size.width / 2f, size.height / 2f)
        })
        val keep = 1f - progress
        val scale = size.width / restPx
        val corner = cardCornerPx * scale * keep
        val crop = android.graphics.Path().apply {
            addRoundRect(
                android.graphics.RectF(
                    0f, 0f,
                    size.width * (1f - bleedXPx / restPx * keep),
                    size.height * (1f - bleedYPx / restPx * keep),
                ),
                // top-start, top-end, bottom-end, bottom-start (x, y each)
                floatArrayOf(0f, 0f, 0f, 0f, corner, corner, 0f, 0f),
                android.graphics.Path.Direction.CW,
            )
        }
        path.op(crop, android.graphics.Path.Op.INTERSECT)
        return Outline.Generic(path.asComposePath())
    }
}

/**
 * The now-playing song as a poster: big title, the album art in a slowly turning cookie
 * shape spilling off the bottom-end corner, and a "Get lyrics" pill low in the card.
 * Up to a third of the screen tall; the whole card is the tap target.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingCard(
    title: String,
    artist: String,
    imgUri: Uri?,
    filePath: String,
    isPlaying: Boolean,
    /** Which way the last song change went: 1 for next, -1 for previous. */
    direction: Int,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedElementsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val cardHeight = (windowHeight / 3).coerceIn(MinCardHeight, MaxCardHeight)
    val compact = cardHeight < CompactCardHeight
    val coverSize = cardHeight * CoverFraction
    val coverPx = with(density) { coverSize.roundToPx() }
    val coverRequest = remember(imgUri, coverPx) {
        ImageRequest.Builder(context).data(imgUri).size(coverPx).build()
    }
    val coverRestPx = with(density) { coverSize.toPx() }
    val lyricsCoverPx = with(density) { LyricsCoverSize.toPx() }
    val morph = remember(lyricsCoverPx) { coverMorph(LyricsCoverCornerPx / lyricsCoverPx) }
    val bleedXPx = with(density) { CoverBleedX.toPx() }
    val bleedYPx = with(density) { CoverBleedY.toPx() }
    val cardCornerPx = with(density) { CardCorner.toPx() }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Only the outline turns; the art stays upright. It turns only while the
    // song plays, so paused stops it where it is; it also holds still while pressed.
    val angle = remember { Animatable(0f) }
    LaunchedEffect(pressed, reduceMotion, isPlaying) {
        if (pressed || reduceMotion || !isPlaying) return@LaunchedEffect
        while (isActive) {
            val remaining = 360f - angle.value
            angle.animateTo(360f, tween((SpinMillis * remaining / 360f).toInt(), easing = LinearEasing))
            angle.snapTo(0f)
        }
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(cardHeight),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Drawn first, so the title can run over it
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = CoverBleedX, y = CoverBleedY)
                    .size(coverSize)
                    .sharedBoundsIf(
                        enabled = sharedElementsEnabled,
                        key = "cover$filePath",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        homeSideCover = true,
                    )
                    // Clipped here, inside the shared bounds, so the same shape follows the cover
                    // as it travels and resizes
                    .graphicsLayer {
                        shape = CoverClipShape(morph, angle.value, coverRestPx, lyricsCoverPx, bleedXPx, bleedYPx, cardCornerPx)
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                CoverArt(request = coverRequest, hasArt = imgUri != null, direction = direction, reduceMotion = reduceMotion)
            }

            Column(Modifier.padding(start = 24.dp, top = 20.dp)) {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "nowPlayingLabel",
                ) { playing ->
                    Text(
                        text = stringResource(if (playing) R.string.now_playing else R.string.last_played).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.1.em,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // A song change slides the details the way you skipped: next pushes them toward
                // the start, previous toward the end
                AnimatedContent(
                    targetState = Track(title, artist, filePath),
                    contentKey = { it.title to it.artist },
                    transitionSpec = {
                        val shift = with(density) { TrackShift.roundToPx() } * if (reduceMotion) 0 else direction
                        (fadeIn(tween(TrackInMillis, delayMillis = TrackOutMillis / 2)) +
                            slideInHorizontally(tween(TrackInMillis, easing = TrackEasing)) { shift }) togetherWith
                            (fadeOut(tween(TrackOutMillis)) +
                                slideOutHorizontally(tween(TrackOutMillis, easing = TrackEasing)) { -shift }) using
                            SizeTransform(clip = false)
                    },
                    label = "nowPlayingTrack",
                ) { track ->
                    Column {
                        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                        // The text wraps its content (inside the width cap), so its shared bounds hug the
                        // words and scale cleanly into the lyrics card's title
                        Box(Modifier.fillMaxWidth(0.68f)) {
                            Text(
                                text = track.title,
                                fontSize = if (compact) 26.sp else 34.sp,
                                lineHeight = if (compact) 27.sp else 35.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.5).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedBoundsIf(
                                    enabled = sharedElementsEnabled,
                                    key = "title${track.filePath}",
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    scaleText = true,
                                ),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.55f)) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedBoundsIf(
                                    enabled = sharedElementsEnabled,
                                    key = "artist${track.filePath}",
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    scaleText = true,
                                ),
                            )
                        }
                    }
                }
            }

            // Not its own button: the whole card is the target, this just says what it does
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(start = 18.dp, end = 24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.get_lyrics),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** The art on screen, and what it is of: the image uri, or null for the placeholder. */
private class ShownArt(val source: Any?, val painter: Painter)

/**
 * The cover's image. A new song's art loads off screen while the old one stays up; once it is
 * ready the two swap with a blur and a slide the way you skipped. The placeholder only shows for
 * a song with no art, never as an in-between.
 */
@Composable
private fun CoverArt(request: ImageRequest, hasArt: Boolean, direction: Int, reduceMotion: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val placeholder = painterResource(R.drawable.ic_song)
    val loader = rememberAsyncImagePainter(model = request, imageLoader = context.imageLoader)
    val state = loader.state
    var shown by remember { mutableStateOf<ShownArt?>(null) }
    LaunchedEffect(state, hasArt) {
        when (val s = state) {
            is AsyncImagePainter.State.Success -> if (shown?.source != request.data) shown = ShownArt(request.data, s.painter)
            is AsyncImagePainter.State.Error -> if (shown?.source != null || shown == null) shown = ShownArt(null, placeholder)
            else -> if (!hasArt && shown?.source != null) shown = ShownArt(null, placeholder)
        }
    }
    if (shown == null && !hasArt) shown = ShownArt(null, placeholder)

    AnimatedContent(
        targetState = shown,
        contentKey = { it?.source },
        transitionSpec = {
            val shift = with(density) { ArtShift.roundToPx() } * if (reduceMotion) 0 else direction
            (fadeIn(tween(TrackInMillis, easing = TrackEasing)) +
                slideInHorizontally(tween(TrackInMillis, easing = TrackEasing)) { shift }) togetherWith
                (fadeOut(tween(TrackInMillis)) +
                    slideOutHorizontally(tween(TrackInMillis, easing = TrackEasing)) { -shift })
        },
        label = "nowPlayingArt",
        modifier = Modifier.fillMaxSize(),
    ) { art ->
        // Sharp when settled, blurred while arriving or leaving
        val blur by transition.animateDp(
            transitionSpec = { tween(TrackInMillis, easing = TrackEasing) },
            label = "artBlur",
        ) { if (it == EnterExitState.Visible || reduceMotion) 0.dp else ArtBlur }
        if (art != null) Image(
            painter = art.painter,
            contentDescription = stringResource(R.string.album_cover),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
        )
    }
}
