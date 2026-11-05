package pl.lambada.songsync.ui.screens.home.components

import pl.lambada.songsync.util.ui.EaseOutStrong
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.FrostedTintAlpha
import pl.lambada.songsync.ui.components.frostedStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeAppBar(
    showing: Boolean,
    /** Whether the list is scrolled away from the top; frosts the bar. */
    scrolled: Boolean,
    /** The song list, blurred behind the bar once it's scrolled. Without it the bar stays solid. */
    hazeState: HazeState?,
    onSelectedClearAction: () -> Unit,
    cachedSize: Int,
    /** Lyrics search for any song, shown when nothing is selected. */
    searchLyricsAction: @Composable () -> Unit = {},
) {
    // Single-line bar that never collapses or expands, so pulling down at the top of the list
    // only drives pull-to-refresh. Its look follows the list position (not nested scroll), so it
    // also updates when jumping with the fast scroller: solid at the top, frosted once the list
    // slides under it (the fill turns see-through and the blur shows), solid again for selection.
    val containerColor by animateColorAsState(
        targetValue = when {
            showing -> MaterialTheme.colorScheme.surfaceVariant
            scrolled && hazeState != null -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = FrostedTintAlpha)
            scrolled -> MaterialTheme.colorScheme.surfaceContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "appBarContainer"
    )
    TopAppBar(
        modifier = if (hazeState != null) Modifier.hazeEffect(hazeState, frostedStyle(containerColor)) else Modifier,
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
            // Fades between the app name and the count when selection starts or ends. Keyed on
            // [showing], not the count: the count changes on every tap and must not animate.
            AnimatedContent(
                targetState = showing,
                transitionSpec = {
                    fadeIn(tween(150, easing = EaseOutStrong)) togetherWith
                        fadeOut(tween(100, easing = EaseOutStrong)) using
                        SizeTransform(clip = false)
                },
                label = "appBarTitle",
            ) { selecting ->
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = if (selecting) stringResource(id = R.string.selected_count, cachedSize)
                    else stringResource(R.string.app_name)
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
        colors = if (hazeState != null) {
            // The haze effect draws the fill
            TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
        } else {
            TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = containerColor)
        },
    )
}