@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.settings.components

import pl.lambada.songsync.ui.components.frostedTopBar
import pl.lambada.songsync.ui.components.frostedTopBarColors
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R

/**
 * Top bar for screens opened from Settings: back arrow and a title that collapses on scroll.
 * The caller connects [scrollBehavior] to its list with `Modifier.nestedScroll` and marks the list
 * with `Modifier.hazeSource(hazeState)`.
 */
@Composable
fun SubScreenTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    /** The screen's scrolling content, blurred behind the bar once it slides under. */
    hazeState: HazeState,
    onBack: () -> Unit,
) {
    MediumFlexibleTopAppBar(
        modifier = Modifier.frostedTopBar(hazeState, scrollBehavior),
        colors = frostedTopBarColors(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        title = { Text(text = title) },
        scrollBehavior = scrollBehavior,
    )
}
