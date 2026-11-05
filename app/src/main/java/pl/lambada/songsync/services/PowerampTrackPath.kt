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
     * @return an absolute path to an existing audio file on shared storage (see [safeSongPath]),
     * or null.
     */
    fun resolve(context: Context, raw: String?, title: String, artist: String?, durationMs: Long?): String? =
        fromExtra(context, raw)?.let(::safeSongPath)
            ?: fromMediaStore(context, title, artist, durationMs)?.let(::safeSongPath)

    private fun fromExtra(context: Context, raw: String?): String? {
        val path = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val candidate = when {
            path.startsWith("/") -> path
            // Poweramp's form: "<volume>/<path>", "primary" being internal storage
            @Suppress("DEPRECATION")
            path.startsWith("primary/") ->
                File(Environment.getExternalStorageDirectory(), path.removePrefix("primary/")).absolutePath
            Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/").containsMatchIn(path) ->
                File("/storage", path).absolutePath // SD card, e.g. 1A2B-3C4D/Music/song.flac
            path.startsWith("content://") -> fromContentUri(context, Uri.parse(path))
            path.startsWith("file://") -> Uri.parse(path).path
            @Suppress("DEPRECATION")
            !path.contains("://") -> File(Environment.getExternalStorageDirectory(), path).absolutePath
            else -> null
        }
        return candidate?.takeIf { File(it).exists() }
    }

    /**
     * [path] made canonical if it is an audio file on shared storage, else null.
     *
     * The path comes from another app's intent, and lyrics are written beside it or into it with
     * SongSync's all-files access, so anything else (the app's own data, a non-audio file, a
     * "../" escape) is refused.
     */
    fun safeSongPath(path: String): String? {
        val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return null
        if (!canonical.startsWith("/storage/")) return null
        if (canonical.substringAfterLast('/').substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS) return null
        return canonical
    }

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "mp2", "flac", "m4a", "m4b", "mp4", "aac", "alac", "ogg", "oga", "opus", "wav", "wave",
        "aif", "aiff", "aifc", "wma", "ape", "wv", "mpc", "tta", "dsf", "dff", "mka", "webm", "3gp", "amr",
    )

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
