package pl.lambada.songsync.ui.screens.home.components

import pl.lambada.songsync.util.ui.EaseOutStrong
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TrackWidth = 40.dp // touch strip; only active while the scroller is shown
private val ThumbHeight = 64.dp // full size while held
private val ThumbWidth = 12.dp
private const val ThumbIdleScaleX = 4f / 12f // 4dp wide when idle
private const val ThumbIdleScaleY = 48f / 64f // 48dp tall when idle
private val GestureExclusionHeight = 96.dp // around the thumb, so the back gesture doesn't win
private val BubbleSize = 56.dp
private const val HideDelayMs = 1500L

/**
 * A draggable thumb on the end edge of a [androidx.compose.foundation.lazy.LazyColumn] for
 * moving quickly through long lists. Appears while the list scrolls, hides after
 * [HideDelayMs] of inactivity, and shows a section bubble while dragged.
 *
 * Scroll position is read during layout (not composition), so the thumb follows the list
 * without recomposing anything per frame.
 *
 * @param itemCount number of scrollable data items (excludes headers/footers).
 * @param firstItemIndex lazy-list index of the first data item (number of header items).
 * @param sectionLabel label for a data item index (0-based), or null for no bubble.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    firstItemIndex: Int,
    contentPadding: PaddingValues,
    sectionLabel: ((Int) -> String?)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentLabel by rememberUpdatedState(sectionLabel)

    // Only worth showing when the list is several screens long.
    val worthShowing by remember {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo.size
            visible > 0 && currentItemCount > visible * 3
        }
    }
    if (!worthShowing) return

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragLabel by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress, dragging) {
        if (listState.isScrollInProgress || dragging) {
            visible = true
        } else {
            delay(HideDelayMs)
            visible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 150 else 200),
        label = "fastScrollerAlpha"
    )
    // Grow while held so the finger doesn't cover/lose it; scale, not size, to avoid relayout.
    val thumbScaleX by animateFloatAsState(
        targetValue = if (dragging) 1f else ThumbIdleScaleX,
        animationSpec = tween(durationMillis = 120, easing = EaseOutStrong),
        label = "fastScrollerThumbWidth"
    )
    val thumbScaleY by animateFloatAsState(
        targetValue = if (dragging) 1f else ThumbIdleScaleY,
        animationSpec = tween(durationMillis = 120, easing = EaseOutStrong),
        label = "fastScrollerThumbHeight"
    )

    /** Fraction of the list scrolled, derived from the list itself. Call from layout/draw only. */
    fun listFraction(): Float {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return 0f
        val first = visibleItems.first()
        val position = (first.index - firstItemIndex) +
                (-first.offset).coerceAtLeast(0) / first.size.coerceAtLeast(1).toFloat()
        val scrollable = (currentItemCount - visibleItems.size).coerceAtLeast(1)
        return (position / scrollable).coerceIn(0f, 1f)
    }

    fun thumbOffsetPx(): Int {
        val fraction = if (dragging) dragFraction else listFraction()
        return (fraction * (trackHeightPx - thumbHeightPx)).roundToInt().coerceAtLeast(0)
    }

    fun onDrag(y: Float) {
        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        dragFraction = ((y - thumbHeightPx / 2) / travel).coerceIn(0f, 1f)
        val target = (dragFraction * (currentItemCount - 1)).roundToInt()
        val label = currentLabel?.invoke(target)
        if (label != dragLabel) {
            if (dragLabel != null && label != null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            dragLabel = label
        }
        // Jump, don't animate: drag events arrive every frame.
        scope.launch { listState.scrollToItem(firstItemIndex + target) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .clearAndSetSemantics { } // decorative; the list already exposes scroll actions
    ) {
        // Track: the touch area. Only accepts input while the scroller is shown, so it doesn't
        // steal touches from the list edge when hidden.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(TrackWidth)
                .onSizeChanged { trackHeightPx = it.height }
                .then(
                    if (visible || dragging) Modifier.pointerInput(Unit) {
                        // Grab on touch-down (no slop) and follow that pointer until it lifts,
                        // wherever it moves, so sideways wobble can't drop the drag.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            dragging = true
                            try {
                                onDrag(down.position.y)
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    onDrag(change.position.y)
                                }
                            } finally {
                                dragging = false
                                dragLabel = null
                            }
                        }
                    } else Modifier
                )
        ) {
            // Back-gesture exclusion: a taller invisible box centred on the thumb.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val extra = (GestureExclusionHeight - ThumbHeight).roundToPx() / 2
                        IntOffset(0, thumbOffsetPx() - extra)
                    }
                    .size(width = TrackWidth, height = GestureExclusionHeight)
                    .then(if (visible || dragging) Modifier.systemGestureExclusion() else Modifier)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffsetPx()) }
                    .padding(end = 4.dp)
                    .size(width = ThumbWidth, height = ThumbHeight)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = thumbScaleX
                        scaleY = thumbScaleY
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .background(
                        color = if (dragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(50)
                    )
            )
        }

        // Section bubble, beside the thumb while dragging.
        AnimatedVisibility(
            visible = dragging && dragLabel != null,
            enter = fadeIn(tween(150, easing = EaseOutStrong)) +
                    scaleIn(
                        animationSpec = tween(150, easing = EaseOutStrong),
                        initialScale = 0.95f,
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    ),
            exit = fadeOut(tween(100)) +
                    scaleOut(
                        animationSpec = tween(100),
                        targetScale = 0.95f,
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val bubblePx = BubbleSize.roundToPx()
                    IntOffset(
                        x = -(TrackWidth + 8.dp).roundToPx(),
                        y = thumbOffsetPx() + (thumbHeightPx.roundToInt() - bubblePx) / 2
                    )
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(BubbleSize)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(
                            topStartPercent = 50,
                            topEndPercent = 50,
                            bottomEndPercent = 10,
                            bottomStartPercent = 50
                        )
                    )
            ) {
                Text(
                    text = dragLabel.orEmpty(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** First letter of [text] for a section bubble; non-letters group under "#". */
fun sectionLetter(text: String?): String? {
    val first = text?.trimStart()?.firstOrNull() ?: return null
    return if (first.isLetter()) first.uppercase() else "#"
}
