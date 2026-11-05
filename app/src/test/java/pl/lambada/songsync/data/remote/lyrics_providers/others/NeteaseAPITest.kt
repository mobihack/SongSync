package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.EmptyQueryException

class NeteaseAPITest {

    private val query = SongInfo(songName = "3005", artistName = "Childish Gambino")

    private val searchResponse = """
        {"result":{"songs":[{"id":28059274,"name":"3005","duration":234215,
          "artists":[{"name":"Childish Gambino"},{"name":"Someone"}]}],"songCount":5},"code":200}
    """.trimIndent()

    private val lyricsResponse = """
        {"lrc":{"lyric":"[00:01.00]No, no, no"},
         "tlyric":{"lyric":"[00:01.00]不，不，不"},
         "romalrc":{"lyric":"[00:01.00]bu bu bu"},"code":200}
    """.trimIndent()

    @Test
    fun `search parses the first song and joins artists`() = runTest {
        val http = FakeHttp { respondJson(searchResponse) }
        val song = NeteaseAPI(http.client).getSongInfo(query)!!

        song.songName shouldBe "3005"
        song.artistName shouldBe "Childish Gambino, Someone"
        song.neteaseID shouldBe 28059274
    }

    @Test
    fun `search uses the cookie-free https endpoint`() = runTest {
        // Regression: a stale hardcoded cookie made Netease return random Chinese songs
        val http = FakeHttp { respondJson(searchResponse) }
        NeteaseAPI(http.client).getSongInfo(query, offset = 2)

        val request = http.requests.single()
        request.url.parameters["limit"] shouldBe "10"
        request.url.protocol.name shouldBe "https"
        request.url.encodedPath shouldBe "/api/search/get"
        request.url.parameters["s"] shouldBe "3005 Childish Gambino"
        request.headers["Cookie"].shouldBeNull()
    }

    @Test
    fun `no results gives null`() = runTest {
        val http = FakeHttp { respondJson("""{"result":{"songCount":0},"code":200}""") }
        NeteaseAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `blank query throws`() = runTest {
        val http = FakeHttp { respondJson(searchResponse) }
        shouldThrow<EmptyQueryException> {
            NeteaseAPI(http.client).getSongInfo(SongInfo(songName = " ", artistName = ""))
        }
    }

    @Test
    fun `lyrics returns the original lyrics only by default`() = runTest {
        val http = FakeHttp { respondJson(lyricsResponse) }
        NeteaseAPI(http.client).getSyncedLyrics(28059274) shouldBe "[00:01.00]No, no, no"
        http.requests.single().url.parameters["id"] shouldBe "28059274"
    }

    @Test
    fun `lyrics request asks for word timings`() = runTest {
        val http = FakeHttp { respondJson(lyricsResponse) }
        NeteaseAPI(http.client).getSyncedLyrics(1)

        val request = http.requests.single()
        request.url.encodedPath shouldBe "/api/song/lyric/v1"
        request.url.parameters["yv"] shouldBe "1"
    }

    @Test
    fun `word-by-word lyrics are preferred when available`() = runTest {
        val http = FakeHttp {
            respondJson(
                """
                {"lrc":{"lyric":"[00:12.77]No matter"},
                 "yrc":{"lyric":"{\"t\":0,\"c\":[]}\n[12770,2460](12770,210,0)No (12980,390,0)matter"},
                 "code":200}
                """.trimIndent()
            )
        }
        NeteaseAPI(http.client).getSyncedLyrics(1) shouldBe
            "[00:12.770]<00:12.770>No <00:12.980>matter<00:13.370>"
    }

    @Test
    fun `lyrics appends translation and romanization when asked`() = runTest {
        val http = FakeHttp { respondJson(lyricsResponse) }
        NeteaseAPI(http.client).getSyncedLyrics(1, includeTranslation = true, includeRomanization = true) shouldBe
            "[00:01.00]No, no, no\n\n[00:01.00]不，不，不\n\n[00:01.00]bu bu bu"
    }

    @Test
    fun `empty lyrics give null`() = runTest {
        val http = FakeHttp { respondJson("""{"lrc":{"lyric":""},"tlyric":null,"romalrc":null,"code":200}""") }
        NeteaseAPI(http.client).getSyncedLyrics(1).shouldBeNull()
    }

    @Test
    fun `picks the search result whose duration matches`() = runTest {
        val http = FakeHttp {
            respondJson(
                """
                {"result":{"songs":[
                  {"id":1,"name":"3005","duration":290000,"artists":[{"name":"Childish Gambino"}]},
                  {"id":2,"name":"3005","duration":234215,"artists":[{"name":"Childish Gambino"}]}
                ],"songCount":2},"code":200}
                """.trimIndent()
            )
        }
        val api = NeteaseAPI(http.client)
        api.getSongInfo(query.copy(durationMs = 234_000))!!.neteaseID shouldBe 2
        api.getSongInfo(query.copy(durationMs = 234_000), offset = 1)!!.neteaseID shouldBe 1
        api.getSongInfo(query.copy(durationMs = 234_000), offset = 2).shouldBeNull()
    }
}
