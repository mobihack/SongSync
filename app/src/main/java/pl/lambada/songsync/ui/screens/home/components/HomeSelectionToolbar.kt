@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R

/**
 * Actions for the songs selected on Home: select all and invert in the toolbar, batch download
 * as its primary action.
 */
@Composable
fun HomeSelectionToolbar(
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onBatchDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onBatchDownload) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.batch_download_lyrics))
            }
        },
    ) {
        IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
        }
        IconButton(onClick = onInvertSelection, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.Deselect, contentDescription = stringResource(R.string.invert_selection))
        }
    }
}
