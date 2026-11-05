@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.ProvidersDialog
import pl.lambada.songsync.util.ProviderChoice

/**
 * The ⋮ button of Home's floating toolbar. Its menu opens upward (the toolbar sits at the
 * bottom); Lyrics provider opens [ProvidersDialog].
 */
@Composable
fun HomeOverflowMenu(
    onNavigateToSettingsSectionRequest: () -> Unit,
    providerChoice: ProviderChoice,
    onProviderChoice: (ProviderChoice) -> Unit,
    onBatchDownloadRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var providersDialog by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Above),
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.provider)) },
                    shape = MenuDefaults.itemShape(0, 3).shape,
                    onClick = {
                        expanded = false
                        providersDialog = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.batch_download_lyrics)) },
                    shape = MenuDefaults.itemShape(1, 3).shape,
                    onClick = {
                        expanded = false
                        onBatchDownloadRequest()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.settings)) },
                    shape = MenuDefaults.itemShape(2, 3).shape,
                    onClick = {
                        expanded = false
                        onNavigateToSettingsSectionRequest()
                    },
                )
            }
        }
    }

    if (providersDialog) ProvidersDialog(
        selected = providerChoice,
        onChoose = onProviderChoice,
        onDismissRequest = { providersDialog = false },
    )
}
