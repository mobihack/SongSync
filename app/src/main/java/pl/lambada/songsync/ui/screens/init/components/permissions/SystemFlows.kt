package pl.lambada.songsync.ui.screens.init.components.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Runs [action] when first composed, and not again when the activity is recreated (rotation, a
 * theme change) while this is still showing: that would open the same system screen twice.
 */
@Composable
fun LaunchOnce(action: () -> Unit) {
    var launched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            action()
        }
    }
}

/**
 * Launches the first of [intents] that some app handles. Some Android builds (Go, TV, some OEMs)
 * lack a settings screen; trying the next one beats crashing.
 *
 * @return false if none could be launched
 */
fun launchFirstAvailable(intents: List<Intent>, launch: (Intent) -> Unit): Boolean =
    intents.any { intent ->
        try {
            launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

/** This app's page in system settings, where any permission can be granted by hand. */
fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
