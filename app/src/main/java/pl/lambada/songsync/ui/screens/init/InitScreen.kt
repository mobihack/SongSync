@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.init

import pl.lambada.songsync.ui.components.ListGroup
import androidx.compose.ui.Alignment
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.ScreenHome
import pl.lambada.songsync.ui.screens.init.components.InitScreenTopBar
import pl.lambada.songsync.ui.screens.init.components.PermissionItem
import pl.lambada.songsync.ui.screens.init.components.permissions.AllFilesAccess
import pl.lambada.songsync.ui.screens.init.components.permissions.NotificationPermission
import pl.lambada.songsync.ui.screens.init.components.permissions.PostNotifications

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            InitScreenTopBar()
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.padding(paddingValues).padding(16.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Hero: the one place a large Expressive shape greets the user
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.SoftBurst.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.init_screen_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
                )
                val showPostNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ListGroup(margin = 0.dp) {
                    row {
                        PermissionItem(
                            title = R.string.all_files_access,
                            description = R.string.all_files_access_description,
                            onClick = { viewModel.allFilesClicked = true },
                            granted = viewModel.allFilesPermissionGranted,
                            shapes = it,
                        )
                        if (viewModel.allFilesClicked) {
                            AllFilesAccess(
                                onGranted = { viewModel.allFilesPermissionGranted = true },
                                onDismiss = { viewModel.allFilesClicked = false }
                            )
                        }
                    }
                    if (showPostNotifications) row {
                        PermissionItem(
                            title = R.string.show_notification_permission,
                            description = R.string.show_notification_description,
                            onClick = { viewModel.notificationClicked = true },
                            granted = viewModel.notificationPermissionGranted,
                            shapes = it,
                        )
                        if (viewModel.notificationClicked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            PostNotifications(
                                onGranted = { viewModel.notificationPermissionGranted = true },
                                onDismiss = { viewModel.notificationClicked = false }
                            )
                        }
                    }
                    row {
                        PermissionItem(
                            title = R.string.notification_access,
                            description = R.string.notification_access_description,
                            onClick = { viewModel.notificationAccessClicked = true },
                            granted = viewModel.notificationAccessPermissionGranted,
                            shapes = it,
                        )
                        if (viewModel.notificationAccessClicked) {
                            NotificationPermission(
                                onGranted = { viewModel.notificationAccessPermissionGranted = true },
                                onDismiss = { viewModel.notificationAccessClicked = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        onClick = {
                            viewModel.onProceed()
                            navController.navigate(ScreenHome) {
                                popUpTo(ScreenHome) { inclusive = true }
                            }
                        },
                        enabled = viewModel.allFilesPermissionGranted
                    ) {
                        Text(stringResource(R.string._continue), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}