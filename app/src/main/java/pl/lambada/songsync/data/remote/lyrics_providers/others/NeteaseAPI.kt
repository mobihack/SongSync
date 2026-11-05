package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import android.util.Log
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.ExperimentalSerializationApi
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.domain.model.lyrics_providers.others.NeteaseLyricsResponse
import pl.lambada.songsync.domain.model.lyrics_providers.others.NeteaseResponse
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.InternalErrorException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.WordSync
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json

class NeteaseAPI(private val client: HttpClient = Ktor.client) {
    private val baseURL = "https://music.163.com/api/"

    // stolen from github https://github.com/0x7d4/syncedlyrics/blob/ab744c9ebb96d310861364142ef95706c36a6b1a/syncedlyrics/providers/netease.py#L19C19-L19C19
    private val reqHeaders = mapOf(
        "Accept" to "application/json",
        "Accept-Language" to "en-US,en;q=0.9,fa;q=0.8",
        "Cache-Control" to "max-age=0",
        "Sec-ch-ua" to "\".Not/A)Brand\";v=\"99\", \"Google Chrome\";v=\"103\", \"Chromium\";v=\"103\"",
        "Sec-ch-ua-mobile" to "?0",
        "Sec-ch-ua-platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36",
    )

    /**
     * Searches for synced lyrics using the song name and artist name.
     * @param query The SongInfo object with songName and artistName fields filled.
     * @return Search result as a SongInfo object.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getSongInfo(query: SongInfo, offset: Int? = 0): SongInfo? {
        val search = "${query.songName} ${query.artistName}"

        if (search.isBlank())
            throw EmptyQueryException()

        val response = client.get(
            // search/get works without a session cookie (search/pc needs one)
            baseURL + "search/get"
        ) {
            reqHeaders.forEach {
                header(it.key, it.value)
            }
            parameter("limit", SEARCH_LIMIT)
            parameter("type", 1)
            parameter("s", search)
        }
        val responseBody = response.bodyAsText(Charsets.UTF_8)

        if (responseBody == "[]" || response.status.value !in 200..299 || responseBody.contains("\"songCount\":0"))
            return null

        val neteaseResponse: NeteaseResponse
        try {
            neteaseResponse = json.decodeFromString(responseBody)
        } catch (e: kotlinx.serialization.MissingFieldException) {
            throw InternalErrorException(Log.getStackTraceString(e))
        }

        // Reorder (never drop) by title/artist/duration so the right version comes first
        val song = SearchMatching.rank(
            neteaseResponse.result.songs, query.songName, query.artistName,
            candidateTitle = { it.name },
            candidateArtist = { it.artists.joinToString(", ") { artist -> artist.name } },
            durationMs = query.durationMs,
            candidateDuration = { it.duration },
            strict = false,
        ).getOrNull(offset ?: 0) ?: return null

        return SongInfo(
            songName = song.name,
            artistName = song.artists.joinToString(", ") { it.name },
            neteaseID = song.id,
            durationMs = song.duration
        )
    }

    private companion object {
        const val SEARCH_LIMIT = 10
    }

    /**
     * Searches for synced lyrics using the song name and artist name.
     * @param id The ID of the song from search results.
     * @return The synced lyrics as a string.
     */
    suspend fun getSyncedLyrics(
        id: Long,
        includeTranslation: Boolean = false,
        includeRomanization: Boolean = false
    ): String? {
        val response = client.get(
            baseURL + "song/lyric/v1"
        ) {
            reqHeaders.forEach {
                header(it.key, it.value)
            }
            parameter("id", id)
            parameter("lv", 1)
            parameter("tv", 1)
            parameter("rv", 1)
            parameter("yv", 1) // word-by-word timings ("yrc")
        }
        val responseBody = response.bodyAsText(Charsets.UTF_8)

        if (response.status.value !in 200..299 || responseBody == "[]")
            return null

        val json = json.decodeFromString<NeteaseLyricsResponse>(responseBody)

        val wordByWord = json.yrc?.lyric
            ?.let { WordSync.render(WordSync.parseYrc(it)) }
            ?.takeIf { it.isNotBlank() }

        if (wordByWord == null && json.lrc.lyric.isEmpty())
            return null

        var lyric = wordByWord ?: json.lrc.lyric

        if (includeTranslation && json.tlyric?.lyric?.isNotEmpty() == true)
            lyric += "\n\n" + json.tlyric.lyric

        if (includeRomanization && json.romalrc?.lyric?.isNotEmpty() == true)
            lyric += "\n\n" + json.romalrc.lyric

        return lyric
    }
}