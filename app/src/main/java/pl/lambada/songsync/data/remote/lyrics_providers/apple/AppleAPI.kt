package pl.lambada.songsync.data.remote.lyrics_providers.apple

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.lambada.songsync.data.remote.PaxMusicHelper
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.domain.model.lyrics_providers.others.AppleMusicSearchResponse
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import java.net.URLEncoder

class AppleAPI(private val client: HttpClient = Ktor.clientFor(Providers.APPLE)) {
    private val lyricsBaseURL = "https://lyrics.paxsenix.org/"
    private val apiBaseURL = "https://amp-api.music.apple.com/v1/catalog/us"
    private val tokenManager = AppleTokenManager(client)

    /**
     * Searches for song information using the song name and artist name.
     * @param query The SongInfo object with songName and artistName fields filled.
     * @param offset The offset used for trying to find a better match or searching again.
     * @return Search result as a SongInfo object.
     */
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val terms = SearchMatching.searchText(query.songName, query.artistName)
        if (terms.isBlank())
            throw EmptyQueryException()
        val search = withContext(Dispatchers.IO) {
            URLEncoder.encode(terms, Charsets.UTF_8.toString())
        }

        return try {
            val token = tokenManager.getToken()
            
            val response = client.get(
                "$apiBaseURL/search?" +
                "term=$search&" +
                "types=songs&" +
                "limit=25&" +
                "l=en-US&" +
                "platform=web&" +
                "format[resources]=map&" +
                "include[songs]=artists&" +
                "extend=artistUrl"
            ) {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:95.0) Gecko/20100101 Firefox/95.0")
                header("Accept", "application/json")
                header("Accept-Language", "en-US,en;q=0.5")
                header("x-apple-renewal", "true")
            }

            val responseBody = response.bodyAsText(Charsets.UTF_8)

            if (response.status.value !in 200..299) {
                // Token might be expired, clear it and retry once
                if (response.status.value == 401) {
                    tokenManager.clearToken()
                }
                return null
            }

            val searchResponse = try {
                json.decodeFromString<AppleMusicSearchResponse>(responseBody)
            } catch (e: Exception) {
                return null
            }

            val songs = searchResponse.results.songs?.data ?: return null
            
            val details = songs.mapNotNull { searchResponse.resources?.songs?.get(it.id) }
            // Reorder (never drop) by title/artist/duration so the right version comes first
            val songDetail = SearchMatching.rank(
                details, query.songName, query.artistName,
                candidateTitle = { it.attributes.name },
                candidateArtist = { it.attributes.artistName },
                durationMs = query.durationMs,
                candidateDuration = { it.attributes.durationInMillis },
                strict = false,
            ).getOrNull(offset) ?: return null
            val songId = songDetail.id
            val attributes = songDetail.attributes

            val artworkUrl = attributes.artwork.url
                .replace("{w}", "100")
                .replace("{h}", "100")
                .replace("{f}", "png")

            SongInfo(
                songName = attributes.name,
                artistName = attributes.artistName,
                songLink = attributes.url,
                albumCoverLink = artworkUrl,
                appleID = songId.toLongOrNull() ?: return null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets synced lyrics using the song ID and returns them as a string formatted as an LRC file.
     * @param id The ID of the song from search results.
     * @param multiPersonWordByWord Flag to format lyrics for multiple persons word by word.
     * @return The synced lyrics as a string.
     */
    suspend fun getSyncedLyrics(id: Long, multiPersonWordByWord: Boolean): String? {
        val response = client.get(
            lyricsBaseURL + "apple-music/lyrics?id=$id"
        )
        val responseBody = response.bodyAsText(Charsets.UTF_8)

        if (response.status.value !in 200..299 || responseBody.isEmpty())
            return null

        return PaxMusicHelper().formatWordByWordLyrics(responseBody, multiPersonWordByWord)
    }
}