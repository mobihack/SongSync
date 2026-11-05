package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.action
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException

class MusixmatchAPITest {

    private val query = SongInfo(songName = "Tell Me the Truth and I'll Believe it", artistName = "Philip Sheppard")

    @Before
    fun resetSession() = MusixmatchAPI.clearSession()

    private fun tokenResponse(token: String = "tok-1", status: Int = 200) =
        """{"message":{"header":{"status_code":$status},"body":{"user_token":"$token"}}}"""

    private fun call(status: Int, body: String) =
        """{"message":{"header":{"status_code":$status},"body":$body}}"""

    /** A macro.subtitles.get response; pass null to make that inner call fail. */
    private fun macro(
        subtitle: String? = "[00:01.00] Line one\n[00:02.00] Line two",
        richsync: String? = null,
        plain: String? = "Line one\nLine two",
        status: Int = 200,
    ): String {
        val track = call(200, """{"track":{"track_name":"Tell Me the Truth","artist_name":"Philip Sheppard",
            "commontrack_id":123,"track_share_url":"https://mxm/track","album_coverart_350x350":"https://mxm/cover.jpg"}}""")
        val subtitles = subtitle?.let {
            call(200, """{"subtitle_list":[{"subtitle":{"subtitle_body":${quote(it)},"subtitle_language":"en"}}]}""")
        } ?: call(404, "[]")
        val rich = richsync?.let { call(200, """{"richsync":{"richsync_body":${quote(it)}}}""") } ?: call(404, "[]")
        val lyrics = plain?.let { call(200, """{"lyrics":{"lyrics_body":${quote(it)}}}""") } ?: call(404, "[]")
        return """{"message":{"header":{"status_code":$status},"body":{"macro_calls":{
            "matcher.track.get":$track,"track.subtitles.get":$subtitles,
            "track.richsync.get":$rich,"track.lyrics.get":$lyrics}}}}"""
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun backend(macroBody: String = macro()) = FakeHttp { request ->
        when (request.action) {
            "token.get" -> respondJson(tokenResponse())
            else -> respondJson(macroBody)
        }
    }

    @Test
    fun `gets a token, then matches the song with it`() = runTest {
        val http = backend()
        MusixmatchAPI(http.client).getSongInfo(query)

        http.requests.map { it.action } shouldBe listOf("token.get", "macro.subtitles.get")
        val macroRequest = http.requests[1]
        macroRequest.url.parameters["usertoken"] shouldBe "tok-1"
        macroRequest.url.parameters["q_track"] shouldBe "Tell Me the Truth and I'll Believe it"
        macroRequest.url.parameters["q_artist"] shouldBe "Philip Sheppard"
    }

    @Test
    fun `requests identify as the mobile app over https`() = runTest {
        val http = backend()
        MusixmatchAPI(http.client).getSongInfo(query)

        http.requests.forEach {
            it.url.protocol.name shouldBe "https"
            it.url.host shouldBe "apic-appmobile.musixmatch.com"
            it.url.parameters["app_id"] shouldBe "mac-ios-v2.0"
            it.headers["X-User-Agent"]!! shouldStartWith "Musixmatch/"
            it.headers["X-Cookie"]!! shouldStartWith "x-mxm-token-guid="
        }
    }

    @Test
    fun `token is cached between searches`() = runTest {
        val http = backend()
        MusixmatchAPI(http.client).getSongInfo(query)
        MusixmatchAPI(http.client).getSongInfo(query)

        http.requests.count { it.action == "token.get" } shouldBe 1
    }

    @Test
    fun `returns track info with synced and plain lyrics`() = runTest {
        val song = MusixmatchAPI(backend().client).getSongInfo(query)!!

        song.songName shouldBe "Tell Me the Truth"
        song.artistName shouldBe "Philip Sheppard"
        song.musixmatchID shouldBe 123
        song.albumCoverLink shouldBe "https://mxm/cover.jpg"
        song.syncedLyrics shouldBe "[00:01.00] Line one\n[00:02.00] Line two"
        song.unsyncedLyrics shouldBe "Line one\nLine two"
        song.availableLanguages.shouldBeEmpty() // no translations via the mobile API
    }

    @Test
    fun `falls back to richsync converted to LRC`() = runTest {
        val richsync = """[{"ts":1.5,"te":2.0,"x":"Hello"},{"ts":65.25,"te":66.0,"x":"World"},{"ts":70.0,"x":""}]"""
        val song = MusixmatchAPI(backend(macro(subtitle = null, richsync = richsync)).client).getSongInfo(query)!!

        song.syncedLyrics shouldBe "[00:01.50]Hello\n[01:05.25]World"
    }

    @Test
    fun `plain lyrics only when nothing is synced`() = runTest {
        val api = MusixmatchAPI(backend(macro(subtitle = null, richsync = null)).client)
        val song = api.getSongInfo(query)!!

        song.syncedLyrics.shouldBeNull()
        api.getLyrics(song, preferUnsynced = true) shouldBe "Line one\nLine two"
        api.getLyrics(song, preferUnsynced = false).shouldBeNull()
    }

    @Test
    fun `poisoned decoy lyrics are rejected`() = runTest {
        val decoy = "[00:01.00] Wob gopini den tefe woxica fero gogoh vudob wiya lorem"
        val song = MusixmatchAPI(backend(macro(subtitle = decoy, plain = "wob gopini den tefe woxica fero gogoh vudob wiya")).client)
            .getSongInfo(query)!!

        song.syncedLyrics.shouldBeNull()
        song.unsyncedLyrics.shouldBeNull()
    }

    @Test
    fun `no match gives null`() = runTest {
        val noMatch = """{"message":{"header":{"status_code":200},"body":{"macro_calls":{
            "matcher.track.get":{"message":{"header":{"status_code":404},"body":[]}}}}}}"""
        MusixmatchAPI(backend(noMatch).client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `only one match exists, so offsets above zero give null`() = runTest {
        val http = backend()
        MusixmatchAPI(http.client).getSongInfo(query, offset = 1).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `rejected token is refreshed and the request retried once`() = runTest {
        var tokens = 0
        var macroCalls = 0
        val http = FakeHttp { request ->
            when (request.action) {
                "token.get" -> respondJson(tokenResponse("tok-${++tokens}"))
                else -> respondJson(if (++macroCalls == 1) macro(status = 401) else macro())
            }
        }
        MusixmatchAPI(http.client).getSongInfo(query)!!.musixmatchID shouldBe 123

        http.requests.filter { it.action == "macro.subtitles.get" }
            .map { it.url.parameters["usertoken"] } shouldBe listOf("tok-1", "tok-2")
    }

    @Test
    fun `persistent rejection reports Musixmatch as blocking`() = runTest {
        val http = backend(macro(status = 401))
        shouldThrow<ProviderUnavailableException> { MusixmatchAPI(http.client).getSongInfo(query) }
    }

    @Test
    fun `blocked token request reports Musixmatch as blocking`() = runTest {
        val http = FakeHttp { respondJson(tokenResponse(status = 401)) }
        shouldThrow<ProviderUnavailableException> { MusixmatchAPI(http.client).getSongInfo(query) }
    }

    @Test
    fun `missing title or artist throws`() = runTest {
        shouldThrow<EmptyQueryException> {
            MusixmatchAPI(backend().client).getSongInfo(SongInfo(songName = "Song", artistName = ""))
        }
    }

    @Test
    fun `sends the track length in seconds when known`() = runTest {
        val http = backend()
        MusixmatchAPI(http.client).getSongInfo(query.copy(durationMs = 254_600))

        http.requests.single { it.action == "macro.subtitles.get" }.url.parameters["q_duration"] shouldBe "255"
    }
}
