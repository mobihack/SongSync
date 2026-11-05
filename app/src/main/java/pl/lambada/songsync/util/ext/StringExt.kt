package pl.lambada.songsync.util.ext

import java.io.File
import java.util.Locale

fun String.lowercaseWithLocale(): String {
    return this.lowercase(Locale.getDefault())
}

/**
 * The lyrics file beside this song file: the same path with its extension swapped for ".lrc"
 * (or ".lrc" added when the file name has none). Null for a null or empty path.
 */
fun String?.toLrcFile(): File? {
    if (this.isNullOrEmpty()) return null
    val dot = lastIndexOf('.')
    // Only a dot in the file name itself starts an extension, not one in a folder name
    val base = if (dot > lastIndexOf('/')) substring(0, dot) else this
    return File("$base.lrc")
}

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) defaultValue
    else try {
        enumValueOf(this)
    } catch (e: IllegalArgumentException) {
        defaultValue
    }