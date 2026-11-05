package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.networking.Ktor
import pl.lambada.songsync.util.networking.Ktor.json
import java.util.Locale
import java.util.UUID

/**
 * Musixmatch provider, talking to Musixmatch's mobile API directly with an anonymous token.
 */
class MusixmatchAPI(private val client: HttpClient = Ktor.clientFor(Providers.MUSIXMATCH)) {
    private val baseURL = "https://apic-appmobile.musixmatch.com/ws/1.1/"
    private val appId = "mac-ios-v2.0"
    private val appVersion = "10.1.1"
    private val userAgent = "Musixmatch/2025120901 CFNetwork/3860.300.31 Darwin/25.2.0"

    /**
     * Matches the song on Musixmatch and returns it together with its lyrics.
     * Musixmatch returns a single best match, so any [offset] above 0 has no result.
     */
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0): SongInfo? {
        val artist = query.artistName.orEmpty().trim()
        val title = query.songName.orEmpty().trim()
        if (artist.isBlank() || title.isBlank())
            throw EmptyQueryException()
        if (offset > 0)
            return null

        val durationSec = query.durationMs?.takeIf { it > 0 }?.let { (it + 500) / 1000 }
        val calls = macroSubtitles(artist, title, durationSec) ?: return null

        val track = calls.body("matcher.track.get")?.obj("track") ?: return null
        val commontrackId = track.long("commontrack_id")

        val synced = calls.body("track.subtitles.get")
            ?.let { it["subtitle_list"] as? JsonArray }
            ?.firstOrNull()?.let { it as? JsonObject }
            ?.obj("subtitle")?.string("subtitle_body")
            ?.takeIf { it.isNotBlank() && !isPoisoned(it) }
            ?: calls.body("track.richsync.get")?.obj("richsync")?.string("richsync_body")
                ?.let { richsyncToLrc(it) }
                ?.takeIf { it.isNotBlank() && !isPoisoned(it) }

        val plain = calls.body("track.lyrics.get")?.obj("lyrics")?.string("lyrics_body")
            ?.takeIf { it.isNotBlank() && !isPoisoned(it) }

        return SongInfo(
            songName = track.string("track_name"),
            artistName = track.string("artist_name"),
            songLink = track.string("track_share_url"),
            albumCoverLink = track.string("album_coverart_350x350")
                ?.takeIf { it.isNotBlank() }
                ?: track.string("album_coverart_100x100"),
            musixmatchID = commontrackId,
            hasSyncedLyrics = synced != null,
            hasUnsyncedLyrics = plain != null,
            syncedLyrics = synced,
            unsyncedLyrics = plain,
        )
    }

    /**
     * Lyrics translations aren't available through the mobile API.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun getLyricsInLanguage(
        songId: Long,
        language: String,
        preferSynced: Boolean = true
    ): String? = null

    /**
     * Returns the lyrics.
     * @param songInfo The SongInfo of the song from search results.
     * @param preferUnsynced Flag to prefer unsynced lyrics when synced lyrics are not available.
     * @return The lyrics as a string or null if the lyrics were not found.
     */
    fun getLyrics(songInfo: SongInfo?, preferUnsynced: Boolean = true): String? {
        return songInfo?.syncedLyrics ?: if (preferUnsynced) songInfo?.unsyncedLyrics else null
    }

    /** Calls macro.subtitles.get, which matches the track and returns all lyric formats. */
    private suspend fun macroSubtitles(
        artist: String,
        title: String,
        durationSec: Long?,
        retry: Boolean = true
    ): JsonObject? {
        val token = getToken()
        val response = get("macro.subtitles.get") {
            parameter("format", "json")
            parameter("namespace", "lyrics_richsynched")
            parameter("optional_calls", "track.richsync")
            parameter("subtitle_format", "lrc")
            parameter("q_artist", artist)
            parameter("q_track", title)
            parameter("usertoken", token)
            durationSec?.let { parameter("q_duration", it) }
        }
        when (response.status()) {
            200 -> {}
            UNAUTHORIZED -> {
                // Expired token, or Musixmatch blocking us: retry once with a fresh token
                userToken = null
                if (retry) return macroSubtitles(artist, title, durationSec, retry = false)
                throw ProviderUnavailableException("Musixmatch is blocking requests right now. Try again later.")
            }
            else -> return null
        }
        return response.message()?.obj("body")?.obj("macro_calls")
    }

    private suspend fun getToken(): String {
        userToken?.let { return it }
        val response = get("token.get") {
            parameter("user_language", "en")
            parameter("guid", guid)
        }
        if (response.status() == UNAUTHORIZED)
            throw ProviderUnavailableException("Musixmatch is blocking requests right now. Try again later.")
        return response.message()?.obj("body")?.string("user_token")
            ?.takeIf { it.isNotBlank() && it != "UpgradeOnlyUpgradeOnlyUpgradeOnlyUpgradeOnly" }
            ?.also { userToken = it }
            ?: throw ProviderUnavailableException("Couldn't get a Musixmatch session. Try again later.")
    }

    private suspend fun get(
        action: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit
    ): JsonObject {
        val response = client.get(baseURL + action) {
            header("x-mxm-app-version", appVersion)
            header("X-User-Agent", userAgent)
            header("X-Cookie", "x-mxm-token-guid=$guid")
            header("Accept", "application/json")
            header("Accept-Language", "en-US,en;q=0.9")
            parameter("app_id", appId)
            parameter("t", System.currentTimeMillis())
            block()
        }
        return json.parseToJsonElement(response.bodyAsText(Charsets.UTF_8)).jsonObject
    }

    /** Converts Musixmatch richsync JSON (one object per line) into line-synced LRC. */
    private fun richsyncToLrc(body: String): String? = try {
        (json.parseToJsonElement(body) as? JsonArray)?.joinToString("\n") { element ->
            val line = element as? JsonObject ?: return@joinToString ""
            val text = line.string("x").orEmpty()
            val ts = (line["ts"] as? JsonPrimitive)?.doubleOrNull
            if (text.isBlank() || ts == null) "" else "${toLrcTimestamp(ts)}$text"
        }?.trim()
    } catch (e: Exception) {
        null
    }

    private fun toLrcTimestamp(seconds: Double): String {
        val totalCentis = (seconds * 100).toLong()
        return String.format(
            Locale.ROOT, "[%02d:%02d.%02d]",
            totalCentis / 6000, (totalCentis / 100) % 60, totalCentis % 100
        )
    }

    /**
     * Musixmatch sometimes returns fake gibberish lyrics to clients it suspects of scraping.
     * Detect the known pattern so it's never saved as real lyrics.
     */
    private fun isPoisoned(text: String): Boolean {
        val normalized = text
            .replace(Regex("""\[[0-9]{1,2}:[0-9]{2}(?:[.:][0-9]{1,3})?]"""), "")
            .lowercase()
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ")
        return normalized.startsWith("wob gopini den tefe woxica fero gogoh vudob wiya")
    }

    private fun JsonObject.message(): JsonObject? = obj("message")
    private fun JsonObject.status(): Int? =
        message()?.obj("header")?.let { (it["status_code"] as? JsonPrimitive)?.intOrNull }

    /** Body of one call inside a macro response, if that call succeeded. */
    private fun JsonObject.body(call: String): JsonObject? {
        val message = obj(call)?.message() ?: return null
        val status = message.obj("header")?.let { (it["status_code"] as? JsonPrimitive)?.intOrNull }
        if (status != 200) return null
        return message["body"] as? JsonObject // failed calls can have "body": []
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    /**
     * Gets the display name for a language code.
     * @param languageCode The ISO language code.
     * @return The display name of the language.
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "it" -> "Italiano"
            "pt" -> "Português"
            "ru" -> "Русский"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "zh" -> "中文"
            "ar" -> "العربية"
            "hi" -> "हिन्दी"
            "tr" -> "Türkçe"
            "pl" -> "Polski"
            "nl" -> "Nederlands"
            "sv" -> "Svenska"
            "da" -> "Dansk"
            "no" -> "Norsk"
            "cs" -> "Čeština"
            "sk" -> "Slovenčina"
            "hu" -> "Magyar"
            "ro" -> "Română"
            "bg" -> "Български"
            "hr" -> "Hrvatski"
            "sr" -> "Српски"
            "sl" -> "Slovenščina"
            "et" -> "Eesti"
            "lv" -> "Latviešu"
            "lt" -> "Lietuvių"
            "uk" -> "Українська"
            "he" -> "עברית"
            "th" -> "ไทย"
            "vi" -> "Tiếng Việt"
            "id" -> "Bahasa Indonesia"
            "ms" -> "Bahasa Melayu"
            "ta" -> "தமிழ்"
            "bn" -> "বাংলা"
            "fa" -> "فارسی"
            "uz" -> "O'zbek"
            "ky" -> "Кыргызча"
            "mn" -> "Монгол"
            "ka" -> "ქართული"
            "az" -> "Azərbaycan"
            "af" -> "Afrikaans"
            "is" -> "Íslenska"
            "mk" -> "Македонски"
            "bs" -> "Bosanski"
            "fi" -> "Finnish"
            "nb" -> "Norsk Bokmål"
            "f1" -> "Filipino"
            "el" -> "Ελληνικά"
            else -> languageCode.uppercase()
        }
    }

    companion object {
        private const val UNAUTHORIZED = 401

        @Volatile
        private var userToken: String? = null

        /** Forgets the cached anonymous token (for tests). */
        internal fun clearSession() {
            userToken = null
        }

        // Random per-install identifier, like the official app sends
        private val guid: String = UUID.randomUUID().toString()
    }
}
