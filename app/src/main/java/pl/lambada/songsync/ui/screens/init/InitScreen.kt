@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.init

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.ScreenHome
import pl.lambada.songsync.ui.components.ListGroup
import pl.lambada.songsync.ui.screens.init.components.PermissionRow
import pl.lambada.songsync.ui.screens.init.components.permissions.AllFilesAccess
import pl.lambada.songsync.ui.screens.init.components.permissions.NotificationPermission
import pl.lambada.songsync.ui.screens.init.components.permissions.PostNotifications

/**
 * First-run setup: the logo and title, then the one required permission and the optional ones
 * in separate groups, each with its own Allow. Continue unlocks once file access is granted.
 */
@Composable
fun InitScreen(
    navController: NavController,
    viewModel: InitScreenViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.onLoad(context)
    }
    Scaffold(
        bottomBar = {
            Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                Button(
                    onClick = {
                        viewModel.onProceed()
                        navController.navigate(ScreenHome) {
                            popUpTo(ScreenHome) { inclusive = true }
                        }
                    },
                    enabled = viewModel.allFilesPermissionGranted,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    Text(
                        stringResource(
                            if (viewModel.allFilesPermissionGranted) R.string._continue else R.string.setup_continue_needs_files
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Header()

            SectionLabel(R.string.setup_required)
            ListGroup {
                row {
                    PermissionRow(
                        icon = Icons.Outlined.FolderOpen,
                        title = R.string.all_files_access,
                        description = R.string.all_files_access_description,
                        granted = viewModel.allFilesPermissionGranted,
                        requesting = viewModel.allFilesClicked,
                        onRequest = { viewModel.allFilesClicked = true },
                        shapes = it,
                    )
                }
            }

            SectionLabel(R.string.setup_optional)
            ListGroup {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) row {
                    PermissionRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = R.string.setup_notifications,
                        description = R.string.show_notification_description,
                        granted = viewModel.notificationPermissionGranted,
                        requesting = viewModel.notificationClicked,
                        onRequest = { viewModel.notificationClicked = true },
                        shapes = it,
                    )
                }
                row {
                    PermissionRow(
                        icon = Icons.Outlined.MusicNote,
                        title = R.string.setup_notification_access,
                        description = R.string.notification_access_description,
                        granted = viewModel.notificationAccessPermissionGranted,
                        requesting = viewModel.notificationAccessClicked,
                        onRequest = { viewModel.notificationAccessClicked = true },
                        shapes = it,
                    )
                }
            }

            Text(
                stringResource(R.string.setup_change_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )
        }
    }

    // The system flows; each clears its flag when it returns
    if (viewModel.allFilesClicked) {
        AllFilesAccess(
            onGranted = { viewModel.allFilesPermissionGranted = true },
            onDismiss = { viewModel.allFilesClicked = false },
        )
    }
    if (viewModel.notificationClicked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PostNotifications(
            onGranted = { viewModel.notificationPermissionGranted = true },
            onDismiss = { viewModel.notificationClicked = false },
        )
    }
    if (viewModel.notificationAccessClicked) {
        NotificationPermission(
            onGranted = { viewModel.notificationAccessPermissionGranted = true },
            onDismiss = { viewModel.notificationAccessClicked = false },
        )
    }
}

/** The logo in a circle, like the launcher icon, above the title and subtitle. */
@Composable
private fun Header() {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 8.dp)) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.setup_title, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: Int) = Text(
    stringResource(text),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp),
)
