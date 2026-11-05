package pl.lambada.songsync.services

import android.content.Context
import kotlinx.serialization.Serializable
import pl.lambada.songsync.util.networking.Ktor.json
import java.io.File

/**
 * Lyrics recently sent to Poweramp, keyed by Poweramp's track id, so the plugin link under the
 * lyrics can offer to save them without searching again. Kept in app files (the cache directory
 * is cleared on every app start) and trimmed to the latest [MAX_ENTRIES].
 */
object PowerampLyricsStore {
    @Serializable
    data class Entry(
        val realId: Long,
        val title: String,
        val artist: String? = null,
        val path: String? = null,
        val provider: String,
        val lyrics: String,
    )

    private const val MAX_ENTRIES = 50

    fun save(context: Context, entry: Entry) = synchronized(this) {
        val dir = dir(context)
        File(dir, "${entry.realId}.json").writeText(json.encodeToString(Entry.serializer(), entry))
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_ENTRIES)
            ?.forEach { it.delete() }
    }

    fun load(context: Context, realId: Long): Entry? = synchronized(this) {
        val file = File(dir(context), "$realId.json")
        if (!file.exists()) return null
        runCatching { json.decodeFromString(Entry.serializer(), file.readText()) }.getOrNull()
    }

    private fun dir(context: Context) = File(context.filesDir, "poweramp_lyrics").apply { mkdirs() }
}
