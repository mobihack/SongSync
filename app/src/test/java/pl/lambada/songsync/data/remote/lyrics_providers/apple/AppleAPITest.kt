package pl.lambada.songsync.data.remote.lyrics_providers.apple

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.notFound
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.testutil.respondText
import io.ktor.http.HttpStatusCode

class AppleAPITest {

    // Current token format starts with "eyJ0" (PR #231); the old regex only matched "eyJh"
    private val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJ0ZXN0In0.c2lnbmF0dXJl"

    private val query = SongInfo(songName = "3005", artistName = "Childish Gambino")

    private val searchResponse = """
        {"results":{"songs":{"data":[{"id":"1440806041","type":"songs","href":"/v1/catalog/us/songs/1440806041"}]}},
         "resources":{"songs":{"1440806041":{"id":"1440806041","type":"songs","attributes":{
           "name":"3005","artistName":"Childish Gambino","albumName":"Because the Internet",
           "artwork":{"url":"https://img/{w}x{h}bb.{f}"},"url":"https://music.apple.com/song/1440806041"}}}}}
    """.trimIndent()

    private val lineLyrics = """
        {"type":"Line","content":[
          {"text":[{"text":"No, no, no","part":false,"timestamp":null,"endtime":null}],
           "timestamp":1000,"oppositeTurn":false,"background":false,"backgroundText":[],"endtime":2000}]}
    """.trimIndent()

    /** Fake Apple: main page -> JS bundle with the token -> catalog search -> lyrics. */
    private fun appleBackend(
        searchStatus: HttpStatusCode = HttpStatusCode.OK,
        jsBody: String = """var a="x";const token="$jwt";""",
    ) = FakeHttp { request ->
        val path = request.url.encodedPath
        when {
            request.url.host == "beta.music.apple.com" && path.startsWith("/assets/index~") -> respondText(jsBody)
            request.url.host == "beta.music.apple.com" ->
                respondText("""<script type="module" src="/assets/index~a1b2c3.js"></script>""")
            path.endsWith("/search") -> respondJson(searchResponse, searchStatus)
            request.url.host == "lyrics.paxsenix.org" -> respondJson(lineLyrics)
            else -> notFound()
        }
    }

    @Test
    fun `search extracts the token from the JS bundle and sends it`() = runTest {
        val http = appleBackend()
        AppleAPI(http.client).getSongInfo(query)

        val search = http.requests.single { it.url.encodedPath.endsWith("/search") }
        search.headers["Authorization"] shouldBe "Bearer $jwt"
        search.url.parameters["term"] shouldBe "3005 Childish Gambino"
    }

    @Test
    fun `search parses the song from the resources map`() = runTest {
        val song = AppleAPI(appleBackend().client).getSongInfo(query)!!

        song.songName shouldBe "3005"
        song.artistName shouldBe "Childish Gambino"
        song.appleID shouldBe 1440806041
        song.albumCoverLink shouldBe "https://img/100x100bb.png"
    }

    @Test
    fun `token is fetched once and reused`() = runTest {
        val http = appleBackend()
        val api = AppleAPI(http.client)
        api.getSongInfo(query)
        api.getSongInfo(query)

        http.requests.count { it.url.host == "beta.music.apple.com" } shouldBe 2 // page + bundle, once
    }

    @Test
    fun `no token in the bundle gives null instead of crashing`() = runTest {
        val http = appleBackend(jsBody = "no token here")
        AppleAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `expired token is dropped so the next search fetches a new one`() = runTest {
        val http = appleBackend(searchStatus = HttpStatusCode.Unauthorized)
        val api = AppleAPI(http.client)
        api.getSongInfo(query).shouldBeNull()
        api.getSongInfo(query).shouldBeNull()

        http.requests.count { it.url.host == "beta.music.apple.com" } shouldBe 4 // fetched twice
    }

    @Test
    fun `offset past the results gives null`() = runTest {
        AppleAPI(appleBackend().client).getSongInfo(query, offset = 1).shouldBeNull()
    }

    @Test
    fun `lyrics are fetched by id and formatted as LRC`() = runTest {
        val http = appleBackend()
        AppleAPI(http.client).getSyncedLyrics(1440806041, multiPersonWordByWord = false) shouldBe
            "[00:01.000]No, no, no"
        http.requests.single().url.parameters["id"] shouldBe "1440806041"
    }

    @Test
    fun `lyrics server errors give null`() = runTest {
        // Seen live: lyrics.paxsenix.org answers 503 when overloaded
        val http = FakeHttp {
            respondJson("""{"message":"Lyrics service currently busy","error":true}""", HttpStatusCode.ServiceUnavailable)
        }
        AppleAPI(http.client).getSyncedLyrics(1, multiPersonWordByWord = false).shouldBeNull()
    }
}
