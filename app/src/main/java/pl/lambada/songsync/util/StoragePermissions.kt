package pl.lambada.songsync.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Whether storage access comes from "All files access" (MANAGE_EXTERNAL_STORAGE), from Android 11
 * on, rather than the runtime storage permissions of older versions. Setup and the launch check
 * must agree on this, or a user is sent back to setup on every launch.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
fun usesAllFilesAccess(): Boolean = usesAllFilesAccess(Build.VERSION.SDK_INT)

/** [usesAllFilesAccess] for Android version [sdkInt]. */
internal fun usesAllFilesAccess(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.R
