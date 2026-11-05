package pl.lambada.songsync.util

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import java.io.File

fun isLegacyFileAccessRequired(filePath: String?): Boolean {
    // Before Android 11, not in internal storage
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            && filePath?.contains("/storage/emulated/0/") == false
}

/**
 * Opens the song at [filePath] in another app, through its MediaStore uri: that works for songs
 * on SD cards too, and shares only that one song.
 */
fun openFileFromPath(context: Context, filePath: String) {
    if (!File(filePath).exists()) {
        showToast(context, "File does not exist")
        return
    }
    val uri = mediaStoreUriFor(context, filePath) ?: run {
        showToast(context, "This song isn't in the music library yet.")
        return
    }

    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "audio/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open the music file.", Toast.LENGTH_SHORT).show()
    }
}

fun showToast(context: Context, messageResId: Int, vararg args: Any, long: Boolean = true) {
    Toast
        .makeText(
            context,
            context.getString(messageResId, *args),
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        )
        .show()
}

fun showToast(context: Context, message: String, long: Boolean = true) {
    Toast
        .makeText(
            context,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        )
        .show()
}
