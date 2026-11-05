package pl.lambada.songsync.ui.screens.init.components.permissions

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import pl.lambada.songsync.util.usesAllFilesAccess

private val STORAGE_PERMISSIONS = listOf(
    android.Manifest.permission.READ_EXTERNAL_STORAGE,
    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AllFilesAccess(
    onGranted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storageManager = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (usesAllFilesAccess() && Environment.isExternalStorageManager()) {
            onGranted()
        }
        onDismiss()
    }
    val storagePermissionState = rememberMultiplePermissionsState(
        STORAGE_PERMISSIONS,
        onPermissionsResult = { results ->
            if (results.values.all { it }) {
                onGranted()
            } else {
                // After "Don't ask again" the system answers "denied" without showing anything, and
                // the rationale flag stays off: only the app's settings page can grant it now
                val activity = context.findActivity()
                val canAskAgain = activity != null &&
                    STORAGE_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                if (!canAskAgain) launchFirstAvailable(listOf(appDetailsIntent(context)), context::startActivity)
            }
            onDismiss()
        }
    )
    LaunchOnce {
        if (usesAllFilesAccess()) {
            val launched = launchFirstAvailable(
                listOf(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    appDetailsIntent(context),
                ),
                storageManager::launch,
            )
            if (!launched) onDismiss()
        } else {
            storagePermissionState.launchMultiplePermissionRequest()
        }
    }
}
