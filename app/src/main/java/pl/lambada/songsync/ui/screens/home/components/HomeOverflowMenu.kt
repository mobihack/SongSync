@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components

import pl.lambada.songsync.ui.components.frosted
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import pl.lambada.songsync.ui.components.providerChoiceLabel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import pl.lambada.songsync.ui.components.ProvidersSheet
import pl.lambada.songsync.util.ProviderChoice

/**
 * Home's ⋮ button, round in the bottom-start corner. Its menu opens upward; Lyrics provider
 * opens [ProvidersSheet].
 */
@Composable
fun HomeOverflowMenu(
    hazeState: HazeState?,
    onNavigateToSettingsSectionRequest: () -> Unit,
    providerChoice: ProviderChoice,
    onProviderChoice: (ProviderChoice) -> Unit,
    onBatchDownloadRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var providersDialog by remember { mutableStateOf(false) }

    Box {
        // A frosted round button matching the floating toolbar on the other side
        Box(Modifier.size(FloatingControlSize).frosted(hazeState, CircleShape), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { expanded = true },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(FloatingControlSize),
            ) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
            }
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Above),
        ) {
            // Two groups: lyrics actions, then Settings on its own
            Column(verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing)) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.provider)) },
                        supportingText = { Text(providerChoiceLabel(providerChoice)) },
                        leadingIcon = { Icon(Icons.Outlined.Route, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        shape = MenuDefaults.itemShape(0, 2).shape,
                        onClick = {
                            expanded = false
                            providersDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.batch_download_lyrics)) },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        shape = MenuDefaults.itemShape(1, 2).shape,
                        onClick = {
                            expanded = false
                            onBatchDownloadRequest()
                        },
                    )
                }
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.settings)) },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        shape = MenuDefaults.itemShape(0, 1).shape,
                        onClick = {
                            expanded = false
                            onNavigateToSettingsSectionRequest()
                        },
                    )
                }
            }
        }
    }

    if (providersDialog) ProvidersSheet(
        selected = providerChoice,
        onChoose = onProviderChoice,
        onDismissRequest = { providersDialog = false },
    )
}
