package pl.lambada.songsync.ui.screens.init.components.permissions

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import pl.lambada.songsync.services.NotificationListener

@Composable
fun NotificationPermission(
    onGranted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val notificationManager = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        ) {
            onGranted()
        }
        onDismiss()
    }
    LaunchedEffect(Unit) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        notificationManager.launch(intent)
    }
}