package pl.lambada.songsync.ui.screens.init

import pl.lambada.songsync.util.usesAllFilesAccess
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import pl.lambada.songsync.data.UserSettingsController

class InitScreenViewModel(
    val userSettingsController: UserSettingsController,
    /** Whether the app has the storage access setup requires; replaceable in tests. */
    private val hasStorageAccess: (Context) -> Boolean = ::hasRequiredStorageAccess,
): ViewModel() {
    var allFilesClicked by mutableStateOf(false)
    var notificationAccessClicked by mutableStateOf(false)

    var allFilesPermissionGranted by mutableStateOf(false)
    var notificationAccessPermissionGranted by mutableStateOf(false)

    fun onLoad(context: Context) {
        allFilesPermissionGranted = hasStorageAccess(context)
        notificationAccessPermissionGranted = NotificationManagerCompat
            .getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun onProceed() {
        userSettingsController.updatePassedInit(true)
    }
}

/** All-files access from Android 11 on, the read and write storage permissions before. */
fun hasRequiredStorageAccess(context: Context): Boolean =
    if (usesAllFilesAccess()) Environment.isExternalStorageManager()
    else listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
