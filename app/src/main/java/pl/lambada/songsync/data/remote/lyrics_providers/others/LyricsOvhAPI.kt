package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json

/**
 * lyrics.ovh: plain-text lyrics looked up by exact artist and title
 * (sourced from Genius, AZLyrics, Lyrics.com and others). The lyrics come with the lookup.
 */
class LyricsOvhAPI(
    private val client: HttpClient = Ktor.client,
    private val baseURL: String = "https://api.lyrics.ovh",
) {
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val artist = query.artistName.orEmpty().trim()
        val title = SearchMatching.cleanTitle(query.songName)
        if (artist.isBlank() || title.isBlank())
            throw EmptyQueryException()
        if (offset > 0)
            return null // an exact lookup has a single result

        val response = client.get("$baseURL/v1/${segment(artist)}/${segment(title)}")
        if (response.status.value != 200)
            return null

        val lyrics = json.decodeFromString<Response>(response.bodyAsText(Charsets.UTF_8)).lyrics
            ?.replace("\r\n", "\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return SongInfo(songName = title, artistName = artist, unsyncedLyrics = lyrics)
    }

    /** Path segment encoding that also escapes "/" and "?" so titles like "AC/DC" stay one segment. */
    private fun segment(value: String) = value.encodeURLPathPart()
        .replace("/", "%2F")
        .replace("?", "%3F")

    @Serializable
    private data class Response(val lyrics: String? = null)
}
