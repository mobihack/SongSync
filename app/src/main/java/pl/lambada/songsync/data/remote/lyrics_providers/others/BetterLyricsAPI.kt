package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.TtmlLyrics
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json

/**
 * BetterLyrics: serves Apple Music's word-timed TTML without an Apple token, but only for songs
 * someone has already looked up. Returns one answer with its lyrics, so no second request is needed.
 */
class BetterLyricsAPI(
    private val client: HttpClient = Ktor.clientFor(Providers.BETTERLYRICS),
    private val baseURL: String = "https://lyrics-api.boidu.dev/getLyrics",
) {
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val title = SearchMatching.cleanTitle(query.songName)
        if (title.isBlank())
            throw EmptyQueryException()
        if (offset > 0)
            return null // BetterLyrics returns a single answer

        val response = client.get(baseURL) {
            parameter("s", title)
            query.artistName?.takeIf { it.isNotBlank() }?.let { parameter("a", it) }
            query.durationMs?.takeIf { it > 0 }?.let { parameter("d", (it + 500) / 1000) }
        }
        when (response.status.value) {
            200 -> {}
            // 401 means the song isn't cached yet (uncached lookups need an API key), not that the
            // provider is down, so the chain should just move on.
            401 -> return null
            429 -> throw ProviderUnavailableException("BetterLyrics is rate limiting requests. Try again later.")
            else -> return null
        }

        val ttml = json.decodeFromString<Response>(response.bodyAsText(Charsets.UTF_8)).ttml ?: return null
        val lrc = TtmlLyrics.toLrc(ttml) ?: return null

        // The response has no title or artist, so the query stands in as the match
        return SongInfo(songName = query.songName, artistName = query.artistName, syncedLyrics = lrc)
    }

    @Serializable
    private data class Response(val ttml: String? = null)
}
