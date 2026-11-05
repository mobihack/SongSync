package pl.lambada.songsync.ui.screens.settings.providers

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.navigation.NavController
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.SettingsHeadLabel
import pl.lambada.songsync.ui.components.WordByWordBadge
import pl.lambada.songsync.ui.screens.settings.components.SubScreenTopBar
import pl.lambada.songsync.util.Providers

/** Lazy-list items before the first provider row: the hint and the "In my list" header. */
private const val HeaderItems = 2

private val ItemPlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * Edits the "My list" provider order: drag the handle to reorder, remove with the minus button,
 * add from the providers below. Providers are tried top to bottom. At least one must remain.
 */
@Composable
fun MyProvidersScreen(
    userSettingsController: UserSettingsController,
    navController: NavController,
) {
    var list by remember { mutableStateOf(userSettingsController.myProviders) }
    val currentList by rememberUpdatedState(list)
    val available = Providers.entries.filter { it.enabled && it !in list }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    var dragging by remember { mutableStateOf<Providers?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun save(new: List<Providers>) {
        list = new
        userSettingsController.updateMyProviders(new)
    }

    fun move(from: Int, to: Int) {
        if (to !in list.indices) return
        save(list.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Swaps the dragged row with a neighbour once its centre passes the neighbour's centre. */
    fun swapIfPastNeighbour() {
        val key = dragging ?: return
        val index = currentList.indexOf(key)
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.find { it.key == key.name } ?: return
        // Layout still shows the previous order: wait for it to catch up before swapping again
        if (current.index != index + HeaderItems) return
        val centre = current.offset + dragOffset + current.size / 2f

        val next = currentList.getOrNull(index + 1)?.let { n -> visible.find { it.key == n.name } }
        val prev = currentList.getOrNull(index - 1)?.let { p -> visible.find { it.key == p.name } }
        when {
            next != null && centre > next.offset + next.size / 2f -> {
                list = currentList.toMutableList().apply { add(index + 1, removeAt(index)) }
                dragOffset -= next.size
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            prev != null && centre < prev.offset + prev.size / 2f -> {
                list = currentList.toMutableList().apply { add(index - 1, removeAt(index)) }
                dragOffset += prev.size
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Scaffold(
        topBar = {
            SubScreenTopBar(
                title = stringResource(R.string.provider_mode_my_list),
                onBack = { navController.popBackStack() },
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.my_list_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            item(key = "listHeader") { SettingsHeadLabel(stringResource(R.string.my_list_in_list)) }

            itemsIndexed(list, key = { _, p -> p.name }) { index, provider ->
                val isDragged = dragging == provider
                val elevation by animateDpAsState(
                    targetValue = if (isDragged) 8.dp else 0.dp,
                    animationSpec = tween(150),
                    label = "dragElevation",
                )
                val moveUp = stringResource(R.string.move_up)
                val moveDown = stringResource(R.string.move_down)
                ProviderRow(
                    provider = provider,
                    position = index + 1,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .then(
                            if (isDragged) Modifier.graphicsLayer {
                                translationY = dragOffset
                                shadowElevation = elevation.toPx()
                            } else Modifier.animateItem(
                                fadeInSpec = tween(150),
                                placementSpec = ItemPlacementSpec,
                                fadeOutSpec = tween(100),
                            )
                        )
                        .background(
                            if (isDragged) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface
                        )
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUp) { move(index, index - 1); true }
                                    .takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) { move(index, index + 1); true }
                                    .takeIf { index < list.lastIndex },
                            )
                        },
                    leading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(provider) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = provider
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset = 0f
                                            save(currentList)
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = 0f
                                            save(currentList)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            swapIfPastNeighbour()
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
                            onClick = { save(list - provider) },
                            enabled = list.size > 1, // a search needs at least one provider
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RemoveCircleOutline,
                                contentDescription = stringResource(R.string.remove_provider, provider.displayName),
                            )
                        }
                    },
                )
            }

            if (available.isNotEmpty()) {
                item(key = "availableHeader") {
                    SettingsHeadLabel(stringResource(R.string.my_list_available))
                }
                items(available, key = { it.name }) { provider ->
                    ProviderRow(
                        provider = provider,
                        position = null,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(150),
                            placementSpec = ItemPlacementSpec,
                            fadeOutSpec = tween(100),
                        ),
                        leading = { Spacer(modifier = Modifier.size(48.dp)) },
                        trailing = {
                            IconButton(onClick = { save(list + provider) }) {
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
    }
}

@Composable
private fun ProviderRow(
    provider: Providers,
    position: Int?,
    modifier: Modifier,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(8.dp))
        if (position != null) {
            Text(
                text = "$position.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
        }
        Text(text = provider.displayName)
        if (provider.hasWordByWord) WordByWordBadge()
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}
