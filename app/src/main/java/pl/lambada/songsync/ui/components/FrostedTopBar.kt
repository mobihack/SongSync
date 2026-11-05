@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * A top bar that frosts once content scrolls under it: solid surface at rest, then the same
 * blurred surface as the floating controls. The screen marks its scrolling content with
 * `Modifier.hazeSource(state)` and gives the bar [frostedTopBarColors], since this draws the fill.
 */
@Composable
fun Modifier.frostedTopBar(state: HazeState, scrollBehavior: TopAppBarScrollBehavior): Modifier {
    val overlapped by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.overlappedFraction > 0.01f }
    }
    val tint by animateColorAsState(
        targetValue = if (overlapped) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = FrostedTintAlpha)
        else MaterialTheme.colorScheme.surface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "frostedTopBar",
    )
    return hazeEffect(state, frostedStyle(tint))
}

/** See-through bar colours: [frostedTopBar] draws the fill. */
@Composable
fun frostedTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
