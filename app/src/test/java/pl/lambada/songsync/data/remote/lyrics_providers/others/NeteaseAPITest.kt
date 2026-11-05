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
        {"result":{"songs":[{"id":28059274,"name":"3005",
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
        request.url.protocol.name shouldBe "https"
        request.url.encodedPath shouldBe "/api/search/get"
        request.url.parameters["s"] shouldBe "3005 Childish Gambino"
        request.url.parameters["offset"] shouldBe "2"
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
}
