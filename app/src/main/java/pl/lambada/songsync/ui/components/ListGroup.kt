@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Side margin of a grouped list, so its rounded corners don't touch the screen edge. */
val ListGroupMargin = 16.dp

/**
 * Colours for grouped rows. M3's segmented default is `surface`, the same as our page background,
 * so rows sit on a container colour instead, a step higher in dark theme to stay visible.
 */
@Composable
fun listGroupColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = listGroupContainerColor(),
)

/**
 * True inside a frosted surface (a [FrostedBottomSheet]), whose tint is the usual row colour: rows
 * there are drawn a step higher so they still stand out.
 */
val LocalOnFrostedSurface = staticCompositionLocalOf { false }

/** Container colour of grouped rows; see [listGroupColors]. */
@Composable
fun listGroupContainerColor(): Color = when {
    LocalOnFrostedSurface.current -> MaterialTheme.colorScheme.surfaceBright
    isSystemInDarkTheme() -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.surfaceContainer
}

/**
 * Draws a hand-built row (one that isn't a `SegmentedListItem`, e.g. a draggable one) as row
 * [index] of a group of [count]. [lifted] rows (being dragged) get large corners all round and a
 * higher container, so they read as picked up out of the group.
 */
@Composable
fun Modifier.listGroupRow(index: Int, count: Int, lifted: Boolean = false): Modifier {
    val shape = if (lifted) MaterialTheme.shapes.large
    else ListItemDefaults.segmentedShapes(index = index, count = count).shape
    val color = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else listGroupContainerColor()
    return this
        .padding(listGroupRowPadding(isLast = index == count - 1))
        .clip(shape)
        .background(color)
}

/** Padding for a row inside a group: side margin, plus the gap to the next row. */
fun listGroupRowPadding(isLast: Boolean) = PaddingValues(
    start = ListGroupMargin,
    end = ListGroupMargin,
    bottom = if (isLast) 0.dp else ListItemDefaults.SegmentedGap,
)

/** Rows collected by [ListGroup]; each one gets its shape from its place in the group. */
class ListGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (shapes: ListItemShapes) -> Unit) {
        rows += content
    }
}

/**
 * A group of rows drawn as one rounded block, the Android 16 Settings look: large outer corners,
 * small inner ones, and a small gap between rows. For short, fixed lists; use [listGroupItems]
 * for lazy, keyed rows that animate.
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    /** Side margin; 0 when the parent already pads its content. */
    margin: Dp = ListGroupMargin,
    content: ListGroupScope.() -> Unit,
) {
    val rows = ListGroupScope().apply(content).rows
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = margin),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        rows.forEachIndexed { index, row ->
            row(ListItemDefaults.segmentedShapes(index = index, count = rows.size))
        }
    }
}

/** Lazy version of [ListGroup]: one keyed item per row, so callers can use `animateItem`. */
inline fun <T> LazyListScope.listGroupItems(
    items: List<T>,
    noinline key: (T) -> Any,
    crossinline itemContent: @Composable LazyItemScope.(item: T, shapes: ListItemShapes, padding: PaddingValues) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        itemContent(
            item,
            ListItemDefaults.segmentedShapes(index = index, count = items.size),
            listGroupRowPadding(isLast = index == items.lastIndex),
        )
    }
}
