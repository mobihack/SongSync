package pl.lambada.songsync.ui.screens.settings.providers

import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import pl.lambada.songsync.ui.components.listGroupRow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.SettingsHeadLabel
import pl.lambada.songsync.ui.components.WordByWordBadge
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.Providers

/** Editor items before the first provider row: the divider, the hint and the "In my list" header. */
private const val EditorHeaderItems = 3

private val ItemPlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Fades in and out and slides into place like the other rows of the editor. */
private fun Modifier.editorItem(scope: LazyItemScope) = with(scope) {
    this@editorItem.animateItem(
        fadeInSpec = tween(150),
        placementSpec = ItemPlacementSpec,
        fadeOutSpec = tween(100),
    )
}

/**
 * State of the "My list" editor: the provider order, and the row being dragged.
 * Changes are saved as they happen; a drag is saved when it ends.
 */
class MyProvidersEditorState internal constructor(
    private val settings: UserSettingsController,
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    /** Items of the surrounding list placed before the editor. */
    private val itemsBefore: Int,
    private val scope: CoroutineScope,
) {
    var list by mutableStateOf(settings.myProviders)
        private set
    var dragging by mutableStateOf<Providers?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    /** The row gliding back into its slot after being dropped, and its remaining offset. */
    var settling by mutableStateOf<Providers?>(null)
        private set
    val settleOffset = Animatable(0f)

    val available: List<Providers> get() = Providers.entries.filter { it.enabled && it !in list }

    fun save(new: List<Providers>) {
        list = new
        settings.updateMyProviders(new)
    }

    /** Whether the list is already the default: every enabled provider, in fallback order. */
    val isDefault: Boolean get() = list == ProviderChain.defaultOrder

    fun reset() = save(ProviderChain.defaultOrder)

    fun move(from: Int, to: Int) {
        if (to !in list.indices) return
        save(list.toMutableList().apply { add(to, removeAt(from)) })
    }

    private var autoScrollJob: Job? = null

    fun startDrag(provider: Providers) {
        // A new drag interrupts a row that is still settling
        settling = null
        scope.launch { settleOffset.snapTo(0f) }
        dragging = provider
        dragOffset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Scrolls while the row is held near an edge, so it can go past the rows on screen
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive && dragging != null) {
                withFrameNanos { }
                autoScrollStep()
            }
        }
    }

    private suspend fun autoScrollStep() {
        val key = dragging ?: return
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.find { it.key == key.name } ?: return
        val top = current.offset + dragOffset
        val delta = autoScrollDelta(
            top = top,
            bottom = top + current.size,
            viewportStart = info.viewportStartOffset.toFloat(),
            viewportEnd = info.viewportEndOffset.toFloat(),
            edge = current.size.toFloat(),
        )
        if (delta == 0f) return
        // The list moved under the finger: move the row back by as much so it stays put
        dragOffset += listState.scrollBy(delta)
        swapIfPastNeighbour()
    }

    fun endDrag() {
        autoScrollJob?.cancel()
        // Glide from where the row was let go instead of snapping into its slot.
        // Undispatched, so the offset is in place before the next frame draws.
        val from = dragOffset
        val dropped = dragging
        settling = dropped
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffset.snapTo(from)
            settleOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (settling == dropped) settling = null
        }
        dragging = null
        dragOffset = 0f
        save(list)
    }

    fun drag(by: Float) {
        dragOffset += by
        swapIfPastNeighbour()
    }

    /** Swaps the dragged row with a neighbour once its centre passes the neighbour's centre. */
    private fun swapIfPastNeighbour() {
        val key = dragging ?: return
        val index = list.indexOf(key)
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.find { it.key == key.name } ?: return
        // Layout still shows the previous order: wait for it to catch up before swapping again
        if (current.index != itemsBefore + EditorHeaderItems + index) return
        val centre = current.offset + dragOffset + current.size / 2f

        val next = list.getOrNull(index + 1)?.let { n -> visible.find { it.key == n.name } }
        val prev = list.getOrNull(index - 1)?.let { p -> visible.find { it.key == p.name } }
        when {
            next != null && centre > next.offset + next.size / 2f -> {
                list = list.toMutableList().apply { add(index + 1, removeAt(index)) }
                dragOffset -= next.size
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            prev != null && centre < prev.offset + prev.size / 2f -> {
                list = list.toMutableList().apply { add(index - 1, removeAt(index)) }
                dragOffset += prev.size
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

/**
 * How far to scroll this frame while a row spanning [top]..[bottom] is dragged: nothing while it
 * is clear of the viewport's [edge]-wide edge zones, then faster the deeper it is in one, up to a
 * quarter of [edge] per frame. Positive scrolls down the list.
 */
internal fun autoScrollDelta(
    top: Float,
    bottom: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edge: Float,
): Float {
    if (edge <= 0f) return 0f
    val maxStep = edge / 4f
    val intoBottom = bottom - (viewportEnd - edge)
    val intoTop = (viewportStart + edge) - top
    return when {
        intoBottom > 0f -> (intoBottom / edge * maxStep).coerceAtMost(maxStep)
        intoTop > 0f -> -(intoTop / edge * maxStep).coerceAtMost(maxStep)
        else -> 0f
    }
}

/**
 * @param listState the state of the LazyColumn the editor is placed in.
 * @param itemsBefore how many items that list has before [myProvidersEditor]'s items.
 */
@Composable
fun rememberMyProvidersEditor(
    settings: UserSettingsController,
    listState: LazyListState,
    itemsBefore: Int,
): MyProvidersEditorState {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(settings, listState, itemsBefore, scope) {
        MyProvidersEditorState(settings, listState, haptics, itemsBefore, scope)
    }
}

/**
 * Edits the "My list" provider order inside a LazyColumn: drag the handle to reorder, remove
 * with the minus button, add from the providers below. Providers are tried top to bottom, and at
 * least one must remain.
 */
fun LazyListScope.myProvidersEditor(editor: MyProvidersEditorState) {
    // Separates the editor from whatever precedes it (on Lyrics search, the mode choices)
    item(key = "myListDivider") {
        Spacer(Modifier.editorItem(this).height(8.dp))
    }
    item(key = "myListHint") {
        Text(
            text = stringResource(R.string.my_list_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .editorItem(this)
                .padding(horizontal = 32.dp, vertical = 8.dp),
        )
    }
    item(key = "myListHeader") {
        var confirmReset by remember { mutableStateOf(false) }
        Row(
            Modifier
                .editorItem(this)
                .fillMaxWidth()
                .padding(end = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f)) { SettingsHeadLabel(stringResource(R.string.my_list_in_list)) }
            TextButton(onClick = { confirmReset = true }, enabled = !editor.isDefault) {
                Text(stringResource(R.string.my_list_reset))
            }
        }
        if (confirmReset) AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.my_list_reset_title)) },
            text = { Text(stringResource(R.string.my_list_reset_message)) },
            confirmButton = {
                TextButton(onClick = { editor.reset(); confirmReset = false }) {
                    Text(stringResource(R.string.my_list_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    itemsIndexed(editor.list, key = { _, p -> p.name }) { index, provider ->
        val isDragged = editor.dragging == provider
        // Stays lifted until it has settled into its slot
        val lifted = isDragged || editor.settling == provider
        val elevation by animateDpAsState(
            targetValue = if (isDragged) 8.dp else 0.dp,
            animationSpec = tween(150),
            label = "dragElevation",
        )
        val moveUp = stringResource(R.string.move_up)
        val moveDown = stringResource(R.string.move_down)
        ProviderRow(
            provider = provider,
            modifier = Modifier
                .zIndex(if (lifted) 1f else 0f)
                .then(
                    if (lifted) Modifier.graphicsLayer {
                        translationY = if (isDragged) editor.dragOffset else editor.settleOffset.value
                        shadowElevation = elevation.toPx()
                    } else Modifier.editorItem(this)
                )
                .listGroupRow(index = index, count = editor.list.size, lifted = lifted)
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(moveUp) { editor.move(index, index - 1); true }
                            .takeIf { index > 0 },
                        CustomAccessibilityAction(moveDown) { editor.move(index, index + 1); true }
                            .takeIf { index < editor.list.lastIndex },
                    )
                },
            leading = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(provider) {
                            detectDragGestures(
                                onDragStart = { editor.startDrag(provider) },
                                onDragEnd = { editor.endDrag() },
                                onDragCancel = { editor.endDrag() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    editor.drag(amount.y)
                                },
                            )
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailing = {
                IconButton(
                    onClick = { editor.save(editor.list - provider) },
                    enabled = editor.list.size > 1, // a search needs at least one provider
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.remove_provider, provider.displayName),
                    )
                }
            },
        )
    }

    val available = editor.available
    if (available.isNotEmpty()) {
        item(key = "availableHeader") {
            Box(Modifier.editorItem(this)) { SettingsHeadLabel(stringResource(R.string.my_list_available)) }
        }
        items(available, key = { it.name }) { provider ->
            ProviderRow(
                provider = provider,
                modifier = Modifier
                    .editorItem(this)
                    .listGroupRow(index = available.indexOf(provider), count = available.size),
                leading = { Spacer(modifier = Modifier.size(48.dp)) },
                trailing = {
                    IconButton(onClick = { editor.save(editor.list + provider) }) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = stringResource(R.string.add_provider, provider.displayName),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ProviderRow(
    provider: Providers,
    modifier: Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Same height as the provider rows above and below (SegmentedListItem, one line)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = provider.displayName)
        if (provider.hasWordByWord) WordByWordBadge()
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}
