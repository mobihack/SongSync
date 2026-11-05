package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.w3c.dom.Element
import org.w3c.dom.Node
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.WordSync
import pl.lambada.songsync.util.ext.toLrcTimestamp
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import javax.xml.parsers.DocumentBuilderFactory

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
        return runCatching { ttmlToLrc(response.bodyAsText(Charsets.UTF_8)) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun ttmlToLrc(ttml: String): String {
        val doc = DocumentBuilderFactory.newInstance().apply {
            // Don't resolve external entities from a remote document. Android's parser rejects
            // this feature (and doesn't resolve external entities anyway), so it's best-effort.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder().parse(ttml.byteInputStream())

        val paragraphs = doc.getElementsByTagName("p")
        val lines = (0 until paragraphs.length).mapNotNull { paragraphs.item(it) as? Element }
        val wordTimed = lines.any { p -> p.childElements().any { it.tagName == "span" && it.hasAttribute("begin") } }

        if (!wordTimed) {
            return lines.mapNotNull { p ->
                val start = parseTime(p.getAttribute("begin")) ?: return@mapNotNull null
                "[${start.toInt().toLrcTimestamp()}]${p.textContent.trim()}"
            }.joinToString("\n")
        }

        return WordSync.render(lines.mapNotNull { p ->
            val start = parseTime(p.getAttribute("begin")) ?: return@mapNotNull null
            val words = mutableListOf<WordSync.Word>()
            for (node in p.childNodes.asList()) {
                when {
                    node is Element && node.tagName == "span" && !node.isBackground() -> {
                        val begin = parseTime(node.getAttribute("begin")) ?: continue
                        val end = parseTime(node.getAttribute("end")) ?: begin
                        words += WordSync.Word(node.textContent, begin, end)
                    }
                    // Spaces between word spans belong to the previous word
                    node.nodeType == Node.TEXT_NODE && words.isNotEmpty() && node.textContent.isNotEmpty() -> {
                        val last = words.removeAt(words.lastIndex)
                        words += last.copy(text = last.text + node.textContent.replace(Regex("""\s+"""), " "))
                    }
                }
            }
            WordSync.Line(start, words)
        })
    }

    private fun Element.isBackground() =
        getAttribute("ttm:role") == "x-bg" || getAttribute("role") == "x-bg"

    private fun Element.childElements() = childNodes.asList().filterIsInstance<Element>()

    private fun org.w3c.dom.NodeList.asList() = (0 until length).map { item(it) }

    /** TTML clock values: "12.345", "1:04.080" or "1:02:03.5", in seconds. */
    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val seconds = value.trim().removeSuffix("s").split(':')
            .fold(0.0) { acc, part -> acc * 60 + (part.toDoubleOrNull() ?: return null) }
        return Math.round(seconds * 1000)
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
