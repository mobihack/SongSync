package pl.lambada.songsync.services

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Turns SongSync's Poweramp lyrics plugin on or off by enabling or disabling its receiver.
 * When off, Poweramp no longer sees SongSync as a lyrics plugin, so it doesn't wait on it.
 * The component state is the setting: Android keeps it across restarts and updates.
 */
object PowerampPlugin {
    private fun receiver(context: Context) = ComponentName(context, PowerampLyricsReceiver::class.java)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(receiver(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            receiver(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
