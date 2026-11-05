package pl.lambada.songsync.util

import android.content.Context
import android.provider.MediaStore

/** Which folders the song list includes. */
enum class FolderMode {
    /** Every folder with music. */
    ALL,

    /** Every folder except the ignored ones. */
    IGNORE,

    /** Only the chosen folders. */
    ONLY,
}

object MusicFolders {
    /**
     * Whether a song in [folder] is shown. A chosen folder also covers its subfolders, so picking
     * "Music" includes "Music/Albums".
     */
    fun includes(folder: String, mode: FolderMode, ignored: Collection<String>, only: Collection<String>): Boolean =
        when (mode) {
            FolderMode.ALL -> true
            FolderMode.IGNORE -> ignored.none { folder.isWithin(it) }
            FolderMode.ONLY -> only.any { folder.isWithin(it) }
        }

    private fun String.isWithin(parent: String) =
        parent.isNotEmpty() && (this == parent || startsWith("$parent/"))

    fun folderOf(filePath: String): String = filePath.substringBeforeLast('/', "")

    /** Folders that contain music, with how many songs each holds, sorted by path. */
    fun load(context: Context): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            null
        )?.use { cursor ->
            val data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val folder = folderOf(cursor.getString(data) ?: continue)
                if (folder.isNotEmpty()) counts[folder] = (counts[folder] ?: 0) + 1
            }
        }
        return counts.entries.map { it.key to it.value }.sortedBy { it.first.lowercase() }
    }
}
