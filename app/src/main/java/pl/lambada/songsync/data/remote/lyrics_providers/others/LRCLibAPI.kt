package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.domain.model.lyrics_providers.others.LRCLibResponse
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LRCLibAPI(private val client: HttpClient = Ktor.clientFor(Providers.LRCLIB)) {
    private val baseURL = "https://lrclib.net/api/"

    /**
     * Searches for synced lyrics using the song name and artist name.
     * @param query The SongInfo object with songName and artistName fields filled.
     * @return Search result as a SongInfo object.
     */
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val terms = SearchMatching.searchText(query.songName, query.artistName)
        if (terms.isBlank())
            throw EmptyQueryException()
        val search = withContext(Dispatchers.IO) {
            URLEncoder.encode(terms, StandardCharsets.UTF_8.toString())
        }

        val response = client.get(
            baseURL + "search?q=$search"
        )
        val responseBody = response.bodyAsText(Charsets.UTF_8)

        if (responseBody == "[]" || response.status.value !in 200..299)
            return null

        val json = json.decodeFromString<List<LRCLibResponse>>(responseBody)

        // Reorder (never drop) by title/artist/duration so the right version comes first
        val song = SearchMatching.rank(
            json, query.songName, query.artistName,
            candidateTitle = { it.trackName },
            candidateArtist = { it.artistName },
            durationMs = query.durationMs,
            candidateDuration = { (it.duration * 1000).toLong() },
            strict = false,
        ).getOrNull(offset) ?: return null

        return SongInfo(
            songName = song.trackName,
            artistName = song.artistName,
            lrcLibID = song.id,
            durationMs = (song.duration * 1000).toLong()
        )
    }

    /**
     * Searches for synced lyrics using the song name and artist name.
     * @param id The ID of the song from search results.
     * @return The synced lyrics as a string.
     */
    suspend fun getSyncedLyrics(id: Int): String? {
        val response = client.get(
            baseURL + "get/$id"
        )
        val responseBody = response.bodyAsText(Charsets.UTF_8)

        if (response.status.value !in 200..299 || responseBody == "[]")
            return null

        val json = json.decodeFromString<LRCLibResponse>(responseBody)
        return json.syncedLyrics
    }
}