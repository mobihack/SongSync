@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
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

/** Library tools on Home: sort and filter in the toolbar, search as its primary action. */
@Composable
fun HomeLibraryToolbar(
    onSort: () -> Unit,
    onFilter: () -> Unit,
    /** A filter is hiding songs; the filter button shows as toggled on. */
    filterActive: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
            }
        },
    ) {
        IconButton(onClick = onSort, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
        }
        FilledTonalIconToggleButton(
            checked = filterActive,
            onCheckedChange = { onFilter() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(containerColor = Color.Transparent),
        ) {
            Icon(Icons.Outlined.FilterAlt, contentDescription = stringResource(R.string.filters))
        }
    }
}

/** "Library ……… 5014 songs" above the song list. */
@Composable
fun LibraryHeader(songCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = pluralStringResource(R.plurals.songs_count, songCount, songCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
