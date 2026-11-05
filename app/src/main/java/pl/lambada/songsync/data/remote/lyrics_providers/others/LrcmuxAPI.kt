package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.WordSync
import pl.lambada.songsync.util.ext.toLrcTimestamp
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import kotlin.math.abs

/**
 * lrcmux: an aggregator that searches Genius, KuGou, Musixmatch, LRCLib, NetEase, YouTube Music
 * and others in one call and returns the best match with plain, line- or word-synced lyrics.
 * The lyrics come with the search result, so no second request is needed.
 */
class LrcmuxAPI(
    private val client: HttpClient = Ktor.clientFor(Providers.LRCMUX),
    private val baseURL: String = "https://api.lrcmux.dev",
) {
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val artist = query.artistName.orEmpty().trim()
        val title = query.songName.orEmpty().trim()
        if (artist.isBlank() || title.isBlank())
            throw EmptyQueryException()
        if (offset > 0)
            return null // lrcmux returns a single best match

        val response = client.get("$baseURL/get") {
            parameter("artist", artist)
            parameter("title", title)
            query.durationMs?.takeIf { it > 0 }?.let { parameter("duration", (it + 500) / 1000) }
        }
        when (response.status.value) {
            200 -> {}
            429 -> throw ProviderUnavailableException("lrcmux is rate limiting requests. Try again later.")
            else -> return null
        }

        val result = json.decodeFromString<Response>(response.bodyAsText(Charsets.UTF_8))
        if (result.meta.instrumental)
            return null

        val durationMs = (result.track.duration * 1000).toLong()
        val wanted = query.durationMs
        if (wanted != null && wanted > 0 && durationMs > 0 && abs(wanted - durationMs) > DURATION_TOLERANCE_MS)
            return null

        val synced = when (result.meta.level) {
            "word" -> WordSync.render(result.lines.map { line ->
                WordSync.Line(
                    line.start ?: 0,
                    line.words.orEmpty().map { WordSync.Word(it.text, it.start, it.end ?: it.start) }
                )
            })
            "line" -> result.lines
                .filter { it.start != null }
                .joinToString("\n") { "[${it.start!!.toInt().toLrcTimestamp()}]${it.text}" }
            else -> null
        }?.takeIf { it.isNotBlank() }

        val plain = result.lines.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
        if (synced == null && plain == null)
            return null

        return SongInfo(
            songName = result.track.title,
            artistName = result.track.artist,
            songLink = result.meta.source?.url,
            albumCoverLink = result.track.cover?.big ?: result.track.cover?.medium,
            durationMs = durationMs.takeIf { it > 0 },
            syncedLyrics = synced,
            unsyncedLyrics = if (synced == null) plain else null,
        )
    }

    @Serializable
    private data class Response(val track: Track, val meta: Meta, val lines: List<Line> = emptyList())

    @Serializable
    private data class Track(
        val title: String? = null,
        val artist: String? = null,
        val duration: Double = 0.0,
        val cover: Cover? = null,
    )

    @Serializable
    private data class Cover(val medium: String? = null, val big: String? = null)

    @Serializable
    private data class Meta(val level: String = "none", val instrumental: Boolean = false, val source: Source? = null)

    @Serializable
    private data class Source(val url: String? = null)

    @Serializable
    private data class Line(val text: String, val start: Long? = null, val end: Long? = null, val words: List<Word>? = null)

    @Serializable
    private data class Word(val text: String, val start: Long, val end: Long? = null)

    private companion object {
        const val DURATION_TOLERANCE_MS = 10_000L
    }
}
