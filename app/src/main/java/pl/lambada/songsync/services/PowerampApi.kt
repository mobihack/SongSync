package pl.lambada.songsync.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** Messages SongSync sends to Poweramp, constants from PowerampAPI (com.maxmpz.poweramp.player). */
object PowerampApi {
    private const val TAG = "PowerampLyrics"

    private const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
    private const val API_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
    private const val ACTION_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
    private const val EXTRA_PACKAGE = "pak"

    /** Sends [intent] to Poweramp's API receiver. */
    fun send(context: Context, intent: Intent): Boolean = runCatching {
        intent
            .setComponent(ComponentName(packageName(context), API_RECEIVER))
            .putExtra(EXTRA_PACKAGE, context.packageName)
        context.sendBroadcast(intent)
    }.onFailure { Log.e(TAG, "Couldn't send ${intent.action} to Poweramp", it) }.isSuccess

    /** Poweramp's package as installed (it differs for some builds), falling back to the usual one. */
    private fun packageName(context: Context): String =
        context.packageManager.resolveService(Intent(ACTION_API_COMMAND), 0)
            ?.serviceInfo?.packageName ?: POWERAMP_PACKAGE
}
