package pl.lambada.songsync.domain.model

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Data class for representing a song.
 * @param title The title of the song.
 * @param artist The artist of the song.
 * @param imgUri The URI of the image.
 * @param filePath The file path of the song.
 */
@Immutable
@Parcelize
data class Song(
    val title: String?,
    val artist: String?,
    val imgUri: Uri?,
    val filePath: String?,
    val durationMs: Long? = null,
) : Parcelable {
    /** The title to search lyrics with: the tagged title, else the file name without extension. */
    val searchTitle: String
        get() = title ?: filePath?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
}