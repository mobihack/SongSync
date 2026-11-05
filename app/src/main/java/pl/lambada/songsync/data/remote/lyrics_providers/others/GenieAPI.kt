package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.ext.toLrcTimestamp
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import kotlin.math.abs

/**
 * Genie Music: line-synced lyrics, strong for Korean music.
 * Search results are scraped from the website; since artists are often listed in Korean,
 * candidates are confirmed by duration (from each song's detail page) rather than artist name.
 */
class GenieAPI(private val client: HttpClient = Ktor.client) {
    private val baseURL = "https://www.genie.co.kr"
    private val lyricsURL = "https://dn.genie.co.kr/app/purchase/get_msl.asp"

    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val title = SearchMatching.cleanTitle(query.songName)
        if (title.isBlank())
            throw EmptyQueryException()

        val page = get("$baseURL/search/searchMain") {
            parameter("query", listOfNotNull(title, query.artistName?.takeIf { it.isNotBlank() }).joinToString(" "))
        } ?: return null

        // Reorder by title only: Genie usually lists artists in Korean
        val candidates = SearchMatching.rank(
            parseCandidates(page), query.songName, null,
            candidateTitle = { it.songName },
            candidateArtist = { it.artistName },
            strict = false,
        )

        val wanted = query.durationMs?.takeIf { it > 0 }
            ?: return candidates.getOrNull(offset)

        // Confirm candidates by duration, probing a few detail pages at most
        var matched = 0
        for (candidate in candidates.take(MAX_PROBE)) {
            val duration = duration(candidate.genieID!!) ?: continue
            if (abs(duration - wanted) > DURATION_TOLERANCE_MS) continue
            if (matched++ == offset) return candidate.copy(durationMs = duration)
        }
        return null
    }

    suspend fun getSyncedLyrics(id: String): String? {
        val body = get(lyricsURL) {
            parameter("path", "a")
            parameter("songid", id)
        } ?: return null
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        val lines = runCatching { json.parseToJsonElement(body.substring(start, end + 1)) as? JsonObject }
            .getOrNull() ?: return null
        return lines.entries
            .mapNotNull { (ms, text) -> ms.toIntOrNull()?.let { it to (text as? JsonPrimitive)?.contentOrNull.orEmpty() } }
            .sortedBy { it.first }
            .joinToString("\n") { (ms, text) -> "[${ms.toLrcTimestamp()}]$text" }
            .takeIf { it.isNotBlank() }
    }

    private suspend fun duration(id: String): Long? {
        val page = get("$baseURL/detail/songInfo") { parameter("xgnm", id) } ?: return null
        val match = durationRegex.find(page) ?: return null
        return (match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1000
    }

    private fun parseCandidates(page: String): List<SongInfo> = rowRegex.findAll(page).map { row ->
        val cells = row.groupValues[2]
        SongInfo(
            songName = cell(cells, "title ellipsis"),
            artistName = cell(cells, "artist ellipsis"),
            songLink = "$baseURL/detail/songInfo?xgnm=${row.groupValues[1]}",
            genieID = row.groupValues[1],
        )
    }.toList()

    /** Text of the link with [cssClass], without badges like "TITLE" and with entities decoded. */
    private fun cell(html: String, cssClass: String): String? {
        val raw = Regex("""class="$cssClass"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?.groupValues?.get(1) ?: return null
        return raw.replace(Regex("""<span class="icon[^"]*"[^>]*>.*?</span>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private suspend fun get(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String? {
        val response = client.get(url) {
            header("User-Agent", USER_AGENT)
            block()
        }
        return when (response.status.value) {
            200 -> response.bodyAsText(Charsets.UTF_8)
            429 -> throw ProviderUnavailableException("Genie is rate limiting requests. Try again later.")
            else -> null
        }
    }

    private companion object {
        const val MAX_PROBE = 5
        const val DURATION_TOLERANCE_MS = 10_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        val rowRegex = Regex("""<tr class="list"\s*songid="(\d+)">(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        val durationRegex = Regex("""alt="재생시간"\s*/></span>\s*<span class="value">(\d{1,2}):(\d{2})<""")
    }
}
