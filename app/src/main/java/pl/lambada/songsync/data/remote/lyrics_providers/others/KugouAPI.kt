package pl.lambada.songsync.data.remote.lyrics_providers.others

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.SearchMatching
import pl.lambada.songsync.util.WordSync
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterOutputStream

/**
 * KuGou: line-synced and word-by-word ("KRC") lyrics, strong for Chinese music.
 * Search finds the song's hash; lyrics are then looked up by hash and downloaded.
 */
class KugouAPI(private val client: HttpClient = Ktor.clientFor(Providers.KUGOU)) {
    // KuGou's mobile search endpoint is only served over plain http
    private val songSearchURL = "http://mobilecdn.kugou.com/api/v3/search/song"
    private val lyricsSearchURL = "https://lyrics.kugou.com/search"
    private val downloadURL = "https://lyrics.kugou.com/download"

    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val artist = query.artistName.orEmpty().trim()
        val title = SearchMatching.cleanTitle(query.songName)
        if (title.isBlank()) throw EmptyQueryException()
        // This provider needs an artist: without one it has no match, and the chain moves on
        if (artist.isBlank()) return null

        val response = get(songSearchURL) {
            parameter("format", "json")
            parameter("keyword", "$title $artist")
            parameter("page", 1)
            parameter("pagesize", SEARCH_LIMIT)
            parameter("showtype", 1)
        } ?: return null
        val songs = json.decodeFromString<SongSearch>(response).data?.info.orEmpty()
            .filter { it.transParam?.language != INSTRUMENTAL }

        // Reorder (never drop) by title/artist/duration so the right version comes first
        val song = SearchMatching.rank(
            songs, query.songName, query.artistName,
            candidateTitle = { it.songName },
            candidateArtist = { it.singerName },
            durationMs = query.durationMs,
            candidateDuration = { it.duration?.times(1000) },
            strict = false,
        ).getOrNull(offset) ?: return null

        return SongInfo(
            songName = song.songName,
            artistName = song.singerName,
            kugouHash = song.hash,
            durationMs = song.duration?.times(1000),
        )
    }

    /** Word-by-word lyrics when KuGou has them, line-synced LRC otherwise. */
    suspend fun getSyncedLyrics(hash: String, durationMs: Long?): String? {
        val search = get(lyricsSearchURL) {
            parameter("ver", 1)
            parameter("man", "yes")
            parameter("client", "mobi")
            parameter("keyword", "")
            parameter("duration", durationMs?.div(1000) ?: 0)
            parameter("hash", hash)
            parameter("album_audio_id", "")
        } ?: return null
        val candidate = json.decodeFromString<LyricsSearch>(search).candidates.firstOrNull() ?: return null

        if (candidate.krcType != 0) {
            val wordByWord = runCatching {
                download(candidate, "krc")?.let { WordSync.render(parseKrc(decodeKrc(it))) }
            }.getOrNull()
            if (!wordByWord.isNullOrBlank()) return wordByWord
        }
        return download(candidate, "lrc")?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
    }

    private suspend fun download(candidate: Candidate, format: String): ByteArray? {
        val response = get(downloadURL) {
            parameter("ver", 1)
            parameter("client", "pc")
            parameter("id", candidate.id)
            parameter("accesskey", candidate.accessKey)
            parameter("fmt", format)
            parameter("charset", "utf8")
        } ?: return null
        val content = json.decodeFromString<Download>(response).content ?: return null
        return Base64.decode(content, Base64.DEFAULT)
    }

    private suspend fun get(url: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String? {
        val response = client.get(url) {
            header("User-Agent", USER_AGENT)
            block()
        }
        return when (response.status.value) {
            200 -> response.bodyAsText(Charsets.UTF_8)
            429 -> throw ProviderUnavailableException("KuGou is rate limiting requests. Try again later.")
            else -> null
        }
    }

    /** KRC lines: `[lineStart,lineDuration]<wordOffset,wordDuration,0>word...`, offsets relative to the line. */
    private fun parseKrc(krc: String): List<WordSync.Line> = krc.lines().mapNotNull { raw ->
        val line = krcLine.find(raw.trim()) ?: return@mapNotNull null
        val start = line.groupValues[1].toLong()
        val words = krcWord.findAll(line.groupValues[2]).map {
            val wordStart = start + it.groupValues[1].toLong()
            WordSync.Word(it.groupValues[3], wordStart, wordStart + it.groupValues[2].toLong())
        }.toList()
        WordSync.Line(start, words)
    }

    @Serializable
    private data class SongSearch(val data: SongSearchData? = null)

    @Serializable
    private data class SongSearchData(val info: List<Song> = emptyList())

    @Serializable
    private data class Song(
        @SerialName("songname") val songName: String? = null,
        @SerialName("singername") val singerName: String? = null,
        val hash: String,
        val duration: Long? = null, // seconds
        @SerialName("trans_param") val transParam: TransParam? = null,
    )

    @Serializable
    private data class TransParam(val language: String? = null)

    @Serializable
    private data class LyricsSearch(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(
        val id: String,
        @SerialName("accesskey") val accessKey: String,
        @SerialName("krctype") val krcType: Int = 0,
    )

    @Serializable
    private data class Download(val content: String? = null)

    companion object {
        private const val SEARCH_LIMIT = 10
        private const val INSTRUMENTAL = "纯音乐"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        private val krcLine = Regex("""^\[(\d+),\d+](.*)$""")
        private val krcWord = Regex("""<(\d+),(\d+),-?\d+>([^<]*)""")

        private val KRC_KEY = intArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69
        )

        /** KRC payload: "krc1" header, then zlib data XORed with a fixed key. */
        fun decodeKrc(payload: ByteArray): String {
            require(payload.size > 4 && String(payload, 0, 4, Charsets.US_ASCII) == "krc1") { "not a KRC payload" }
            val body = ByteArray(payload.size - 4) { i -> (payload[i + 4].toInt() xor KRC_KEY[i % KRC_KEY.size]).toByte() }
            val out = ByteArrayOutputStream()
            InflaterOutputStream(out).use { it.write(body) }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
