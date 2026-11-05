package pl.lambada.songsync.util

import android.os.Build

/**
 * Whether storage access comes from "All files access" (MANAGE_EXTERNAL_STORAGE), from Android 11
 * on, rather than the runtime storage permissions of older versions. Setup and the launch check
 * must agree on this, or a user is sent back to setup on every launch.
 */
fun usesAllFilesAccess(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.R
