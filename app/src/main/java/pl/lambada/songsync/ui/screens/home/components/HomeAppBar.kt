package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeAppBar(
    showing: Boolean,
    /** Whether the list is scrolled away from the top; tints the bar. */
    scrolled: Boolean,
    onSelectedClearAction: () -> Unit,
    cachedSize: Int,
    /** Lyrics search for any song, shown when nothing is selected. */
    searchLyricsAction: @Composable () -> Unit = {},
) {
    // Single-line bar that never collapses or expands, so pulling down at the top of the list
    // only drives pull-to-refresh. Tint follows the list position (not nested scroll), so it
    // also updates when jumping with the fast scroller.
    val containerColor by animateColorAsState(
        targetValue = when {
            showing -> MaterialTheme.colorScheme.surfaceVariant
            scrolled -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "appBarContainer"
    )
    TopAppBar(
        navigationIcon = {
            if (showing) {
                IconButton(onClick = onSelectedClearAction) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        },
        title = {
            if (showing) {
                // No animation: the count changes on every tap in multi-select.
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(id = R.string.selected_count, cachedSize)
                )
            } else {
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.app_name)
                )
            }
        },
        actions = {
            // Selection actions live in the floating toolbar at the bottom (HomeSelectionToolbar)
            if (!showing) {
                // The ⋮ menu lives in the floating toolbar at the bottom (HomeOverflowMenu)
                searchLyricsAction()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
        ),
    )
}