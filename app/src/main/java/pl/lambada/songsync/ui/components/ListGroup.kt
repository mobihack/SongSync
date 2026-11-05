@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Side margin of a grouped list, so its rounded corners don't touch the screen edge. */
val ListGroupMargin = 16.dp

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
fun ListGroup(modifier: Modifier = Modifier, content: ListGroupScope.() -> Unit) {
    val rows = ListGroupScope().apply(content).rows
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ListGroupMargin),
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
