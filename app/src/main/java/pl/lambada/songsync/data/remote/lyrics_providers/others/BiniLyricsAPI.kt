package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.TtmlLyrics
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json

/**
 * BiniLyrics (lrc.red): searches by artist and title and serves lyrics as TTML files,
 * often with word timings. TTML is converted to SongSync's word-by-word or line LRC.
 */
class BiniLyricsAPI(
    private val client: HttpClient = Ktor.client,
    private val baseURL: String = "https://lrc.red/api/v1",
) {
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val title = SearchMatching.cleanTitle(query.songName)
        if (title.isBlank())
            throw EmptyQueryException()

        val response = client.get(baseURL) {
            parameter("q", listOfNotNull(query.artistName?.takeIf { it.isNotBlank() }, title).joinToString(" "))
        }
        when (response.status.value) {
            200 -> {}
            429 -> throw ProviderUnavailableException("BiniLyrics is rate limiting requests. Try again later.")
            else -> return null
        }
        val results = json.decodeFromString<Search>(response.bodyAsText(Charsets.UTF_8)).results

        val match = SearchMatching.rank(
            results, query.songName, query.artistName,
            candidateTitle = { it.trackName },
            candidateArtist = { it.artistName },
            durationMs = query.durationMs,
            candidateDuration = { it.duration?.times(1000) },
        ).getOrNull(offset) ?: return null

        return SongInfo(
            songName = match.trackName,
            artistName = match.artistName,
            songLink = match.lyricsUrl,
            durationMs = match.duration?.times(1000),
        )
    }

    /** Downloads the TTML at [lyricsUrl] and converts it to word-by-word or line LRC. */
    suspend fun getSyncedLyrics(lyricsUrl: String): String? {
        val response = client.get(lyricsUrl)
        if (response.status.value != 200)
            return null
        return TtmlLyrics.toLrc(response.bodyAsText(Charsets.UTF_8))
    }

    @Serializable
    private data class Search(val results: List<Result> = emptyList())

    @Serializable
    private data class Result(
        @SerialName("track_name") val trackName: String? = null,
        @SerialName("artist_name") val artistName: String? = null,
        val duration: Long? = null, // seconds
        val lyricsUrl: String,
    )
}
