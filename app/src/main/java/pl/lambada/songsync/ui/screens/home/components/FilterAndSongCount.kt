package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterAndSongCount(
    displaySongsCount: Int,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    onSearchClick: () -> Unit,
    /** A filter is hiding songs; the filter button shows as toggled on. */
    filterActive: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = pluralStringResource(R.plurals.songs_count, displaySongsCount, displaySongsCount))
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSortClick, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort),
            )
        }

        FilledTonalIconToggleButton(
            checked = filterActive,
            onCheckedChange = { onFilterClick() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Icon(
                Icons.Outlined.FilterAlt,
                contentDescription = stringResource(R.string.filters),
            )
        }

        IconButton(onClick = onSearchClick, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
            )
        }
    }
}