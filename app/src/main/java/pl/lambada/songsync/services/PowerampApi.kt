package pl.lambada.songsync.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import java.io.File

/** Messages SongSync sends to Poweramp, constants from PowerampAPI (com.maxmpz.poweramp.player). */
object PowerampApi {
    private const val TAG = "PowerampLyrics"

    private const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
    private const val API_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
    private const val ACTION_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
    private const val EXTRA_PACKAGE = "pak"

    private const val ACTION_SCAN_DIRS = "com.maxmpz.audioplayer.ACTION_SCAN_DIRS"
    private const val EXTRA_SCAN_PATH = "path"
    private const val EXTRA_FAST_SCAN = "fastScan"
    private const val EXTRA_CAUSE = "cause"

    /** Sends [intent] to Poweramp's API receiver. */
    fun send(context: Context, intent: Intent): Boolean = runCatching {
        intent
            .setComponent(ComponentName(packageName(context), API_RECEIVER))
            .putExtra(EXTRA_PACKAGE, context.packageName)
        context.sendBroadcast(intent)
    }.onFailure { Log.e(TAG, "Couldn't send ${intent.action} to Poweramp", it) }.isSuccess

    /**
     * Asks Poweramp to rescan the folder holding [songPath], so it picks up a new .lrc file or
     * lyrics embedded in the song's tags. A targeted scan is always fast: only changed files.
     */
    fun rescanFolderOf(context: Context, songPath: String) {
        val folder = File(songPath).parent ?: return
        Log.i(TAG, "Asking Poweramp to rescan $folder")
        send(
            context,
            Intent(ACTION_SCAN_DIRS)
                .putExtra(EXTRA_SCAN_PATH, toPowerampPath(folder) + "/")
                .putExtra(EXTRA_FAST_SCAN, true)
                .putExtra(EXTRA_CAUSE, "SongSync+ saved lyrics"),
        )
    }

    /** Poweramp's database form: "primary/…" for internal storage, "<volume>/…" for SD cards. */
    @Suppress("DEPRECATION")
    internal fun toPowerampPath(absolute: String): String {
        val internal = Environment.getExternalStorageDirectory().absolutePath
        return when {
            absolute == internal || absolute.startsWith("$internal/") ->
                "primary" + absolute.removePrefix(internal)
            absolute.startsWith("/storage/") -> absolute.removePrefix("/storage/")
            else -> absolute
        }
    }

    /** Poweramp's package as installed (it differs for some builds), falling back to the usual one. */
    private fun packageName(context: Context): String =
        context.packageManager.resolveService(Intent(ACTION_API_COMMAND), 0)
            ?.serviceInfo?.packageName ?: POWERAMP_PACKAGE
}
