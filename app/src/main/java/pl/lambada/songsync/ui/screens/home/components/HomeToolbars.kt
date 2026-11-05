package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.frosted

/** Height of the floating controls: the toolbar pill, its FAB and the ⋮ button. */
val FloatingControlSize = FloatingToolbarDefaults.ContainerSize

/** A frosted pill of icon buttons, the toolbar half of the floating controls. */
@Composable
fun FrostedToolbar(hazeState: HazeState?, content: @Composable RowScope.() -> Unit) = Row(
    Modifier
        .height(FloatingControlSize)
        .frosted(hazeState, CircleShape)
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

/** A frosted round button with a stronger [tint]: the primary action beside a toolbar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrostedFab(
    hazeState: HazeState?,
    onClick: () -> Unit,
    tint: Color,
    contentColor: Color,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) = Box(Modifier.size(FloatingControlSize).frosted(hazeState, shape, tint), contentAlignment = Alignment.Center) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        IconButton(onClick = onClick, modifier = Modifier.size(FloatingControlSize), shapes = IconButtonDefaults.shapes()) {
            content()
        }
    }
}

/**
 * Actions for the songs selected on Home: select all and invert in the toolbar, batch download
 * as its primary action.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeSelectionToolbar(
    hazeState: HazeState?,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onBatchDownload: () -> Unit,
) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    FrostedToolbar(hazeState) {
        IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
        }
        IconButton(onClick = onInvertSelection, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.Deselect, contentDescription = stringResource(R.string.invert_selection))
        }
    }
    FrostedFab(
        hazeState,
        onClick = onBatchDownload,
        tint = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.batch_download_lyrics))
    }
}

/** Library tools on Home: sort and filter in a frosted toolbar, search as its primary action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeLibraryToolbar(
    hazeState: HazeState?,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    /** A filter is hiding songs; the filter button shows a dot. */
    filterActive: Boolean,
    onSearch: () -> Unit,
) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    FrostedToolbar(hazeState) {
        IconButton(onClick = onSort, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.sort))
        }
        VerticalDivider(
            modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        // A dot on the icon (not a filled button) marks that a filter is hiding songs
        IconButton(
            onClick = onFilter,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.semantics { if (filterActive) stateDescription = "On" },
        ) {
            BadgedBox(badge = { if (filterActive) Badge() }) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = stringResource(R.string.filters))
            }
        }
    }
    FrostedFab(
        hazeState,
        onClick = onSearch,
        // The app's bright blue (same as the Get lyrics button)
        tint = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
    }
}

/** "Library ……… 5014 songs" above the song list. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = pluralStringResource(R.plurals.songs_count, songCount, songCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
