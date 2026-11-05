@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.ProvidersDropdownMenuContent
import pl.lambada.songsync.util.ProviderChoice

@Composable
fun HomeTopAppBarDropDown(
    onNavigateToSettingsSectionRequest: () -> Unit,
    providerChoice: ProviderChoice,
    onProviderChoice: (ProviderChoice) -> Unit,
    onBatchDownloadRequest: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var expandedProviders by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            expandedProviders = false
            expanded = true
        }
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More"
        )
    }
    val motionScheme = MaterialTheme.motionScheme
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        AnimatedContent(
            targetState = expandedProviders,
            label = "",
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    ),
                    initialContentExit = slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = motionScheme.fastSpatialSpec()
                    ),
                )
            },
        ) {
            if(it)
                ProvidersDropdownMenuContent(
                    onDismissRequest = { expanded = false },
                    selected = providerChoice,
                    onChoose = onProviderChoice,
                )
            else
                HomeDropdownContent(
                    onExpandProviders = { expandedProviders = true },
                    onBatchDownloadRequest = {
                        expanded = false
                        onBatchDownloadRequest()
                    },
                    onNavigateToSettingsSectionRequest = {
                        expanded = false
                        onNavigateToSettingsSectionRequest()
                    },
                )
        }
    }
}

@Composable
fun HomeDropdownContent(
    onExpandProviders: () -> Unit,
    onBatchDownloadRequest: () -> Unit,
    onNavigateToSettingsSectionRequest: () -> Unit,
) = DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.provider)) },
        shape = MenuDefaults.itemShape(0, 3).shape,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                contentDescription = null,
            )
        },
        onClick = onExpandProviders
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.batch_download_lyrics)) },
        shape = MenuDefaults.itemShape(1, 3).shape,
        onClick = onBatchDownloadRequest
    )
    DropdownMenuItem(
        text = { Text(stringResource(id = R.string.settings)) },
        shape = MenuDefaults.itemShape(2, 3).shape,
        onClick = onNavigateToSettingsSectionRequest
    )
}
