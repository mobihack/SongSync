package pl.lambada.songsync.services

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlin.math.abs

/** Finds the local file of a track Poweramp asked about, so lyrics can be saved next to it. */
object PowerampTrackPath {

    /**
     * @param raw Poweramp's path extra: an absolute path, a content:// uri, a path relative to
     * storage, or missing.
     * @return an absolute path to an existing file, or null.
     */
    fun resolve(context: Context, raw: String?, title: String, artist: String?, durationMs: Long?): String? {
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            when {
                path.startsWith("/") -> if (File(path).exists()) return path
                // Poweramp's form: "<volume>/<path>", "primary" being internal storage
                path.startsWith("primary/") -> {
                    @Suppress("DEPRECATION")
                    val file = File(Environment.getExternalStorageDirectory(), path.removePrefix("primary/"))
                    if (file.exists()) return file.absolutePath
                }
                Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/").containsMatchIn(path) -> {
                    val file = File("/storage", path) // SD card, e.g. 1A2B-3C4D/Music/song.flac
                    if (file.exists()) return file.absolutePath
                }
                path.startsWith("content://") -> fromContentUri(context, Uri.parse(path))?.let { return it }
                path.startsWith("file://") -> Uri.parse(path).path?.takeIf { File(it).exists() }?.let { return it }
                !path.contains("://") -> {
                    @Suppress("DEPRECATION")
                    val onStorage = File(Environment.getExternalStorageDirectory(), path)
                    if (onStorage.exists()) return onStorage.absolutePath
                }
            }
        }
        return fromMediaStore(context, title, artist, durationMs)
    }

    @Suppress("DEPRECATION")
    private fun fromContentUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?.takeIf { File(it).exists() }
    }.getOrNull()

    /** The library song with this title (and artist, if known) whose length is closest. */
    @Suppress("DEPRECATION")
    private fun fromMediaStore(context: Context, title: String, artist: String?, durationMs: Long?): String? =
        runCatching {
            val selection = buildString {
                append("${MediaStore.Audio.Media.TITLE} = ? COLLATE NOCASE")
                if (artist != null) append(" AND ${MediaStore.Audio.Media.ARTIST} = ? COLLATE NOCASE")
            }
            val args = listOfNotNull(title, artist).toTypedArray()
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION),
                selection, args, null,
            )?.use { cursor ->
                val candidates = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getLong(1))
                }.filter { (path, _) -> path != null && File(path).exists() }
                candidates.minByOrNull { (_, length) -> durationMs?.let { abs(length - it) } ?: 0L }?.first
            }
        }.getOrNull()
}
