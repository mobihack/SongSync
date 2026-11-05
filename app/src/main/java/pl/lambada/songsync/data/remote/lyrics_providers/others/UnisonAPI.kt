package pl.lambada.songsync.data.remote.lyrics_providers.others

import android.util.Log
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
 * Unison: lyrics uploaded and voted on by listeners, often word-timed TTML. Returns one best
 * match with its lyrics, so no second request is needed.
 */
class UnisonAPI(
    private val client: HttpClient = Ktor.clientFor(Providers.UNISON),
    private val baseURL: String = "https://unison.boidu.dev/lyrics",
) {
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val title = SearchMatching.cleanTitle(query.songName)
        if (title.isBlank())
            throw EmptyQueryException()
        if (offset > 0)
            return null // Unison returns a single best match

        val response = client.get(baseURL) {
            parameter("song", title)
            query.artistName?.takeIf { it.isNotBlank() }?.let { parameter("artist", it) }
            query.durationMs?.takeIf { it > 0 }?.let { parameter("duration", (it + 500) / 1000) }
        }
        when (response.status.value) {
            200 -> {}
            429 -> throw ProviderUnavailableException("Unison is rate limiting requests. Try again later.")
            else -> return null
        }

        // score, voteCount and confidence are ignored on purpose: new uploads have no votes yet
        val data = json.decodeFromString<Response>(response.bodyAsText(Charsets.UTF_8)).data ?: return null
        val lyrics = data.lyrics?.takeIf { it.isNotBlank() } ?: return null
        val lrc = when {
            data.format.equals("ttml", ignoreCase = true) -> TtmlLyrics.toLrc(lyrics)
            lrcStart.containsMatchIn(lyrics) -> lyrics.trim()
            else -> {
                Log.w(TAG, "Unsupported lyrics format: ${data.format}")
                null
            }
        } ?: return null

        return SongInfo(songName = data.song, artistName = data.artist, syncedLyrics = lrc)
    }

    @Serializable
    private data class Response(val data: Data? = null)

    @Serializable
    private data class Data(
        val song: String? = null,
        val artist: String? = null,
        val lyrics: String? = null,
        val format: String? = null,
    )

    private companion object {
        const val TAG = "UnisonAPI"
        val lrcStart = Regex("""^\[\d{1,3}:\d{2}""", RegexOption.MULTILINE)
    }
}
