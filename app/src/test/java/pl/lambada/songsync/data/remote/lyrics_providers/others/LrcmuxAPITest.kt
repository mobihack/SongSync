package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.notFound
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException

class LrcmuxAPITest {

    private val query = SongInfo(songName = "Bohemian Rhapsody", artistName = "Queen")

    private fun response(level: String, lines: String, duration: Int = 354, instrumental: Boolean = false) = """
        {"track":{"cover":{"big":"https://cdn/big.jpg"},"title":"Bohemian Rhapsody","artist":"Queen",
          "album":"A Night At The Opera","duration":$duration},
         "meta":{"source":{"id":"deezer","name":"Deezer","url":"https://www.deezer.com"},
          "level":"$level","instrumental":$instrumental},
         "lines":$lines}
    """.trimIndent()

    private val wordLines = """[
        {"text":"Is this","start":287,"end":937,"words":[
          {"text":"Is ","start":287,"end":550},{"text":"this","start":625,"end":937}]}]"""

    private val lineLines = """[{"text":"Is this the real life?","start":287,"end":2975},
        {"text":"Is this just fantasy?","start":4080,"end":5867}]"""

    @Test
    fun `asks for the song by artist, title and duration`() = runTest {
        val http = FakeHttp { respondJson(response("line", lineLines)) }
        LrcmuxAPI(http.client).getSongInfo(query.copy(durationMs = 354_400))

        val request = http.requests.single()
        request.url.toString().substringBefore('?') shouldBe "https://api.lrcmux.dev/get"
        request.url.parameters["artist"] shouldBe "Queen"
        request.url.parameters["title"] shouldBe "Bohemian Rhapsody"
        request.url.parameters["duration"] shouldBe "354"
    }

    @Test
    fun `word level lyrics come back word-by-word`() = runTest {
        val song = LrcmuxAPI(FakeHttp { respondJson(response("word", wordLines)) }.client).getSongInfo(query)!!

        song.songName shouldBe "Bohemian Rhapsody"
        song.artistName shouldBe "Queen"
        song.albumCoverLink shouldBe "https://cdn/big.jpg"
        song.durationMs shouldBe 354_000
        song.syncedLyrics shouldBe "[00:00.287]<00:00.287>Is <00:00.550><00:00.625>this<00:00.937>"
    }

    @Test
    fun `line level lyrics come back as LRC`() = runTest {
        val song = LrcmuxAPI(FakeHttp { respondJson(response("line", lineLines)) }.client).getSongInfo(query)!!
        song.syncedLyrics shouldBe "[00:00.287]Is this the real life?\n[00:04.080]Is this just fantasy?"
    }

    @Test
    fun `unsynced lyrics come back as plain text`() = runTest {
        val plain = """[{"text":"Is this the real life?"},{"text":"Is this just fantasy?"}]"""
        val song = LrcmuxAPI(FakeHttp { respondJson(response("none", plain)) }.client).getSongInfo(query)!!

        song.syncedLyrics.shouldBeNull()
        song.unsyncedLyrics shouldBe "Is this the real life?\nIs this just fantasy?"
    }

    @Test
    fun `a match whose duration is far off is rejected`() = runTest {
        val http = FakeHttp { respondJson(response("line", lineLines, duration = 600)) }
        LrcmuxAPI(http.client).getSongInfo(query.copy(durationMs = 354_000)).shouldBeNull()
    }

    @Test
    fun `instrumental tracks have no lyrics`() = runTest {
        val http = FakeHttp { respondJson(response("none", "[]", instrumental = true)) }
        LrcmuxAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `not found gives null`() = runTest {
        LrcmuxAPI(FakeHttp { notFound() }.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `rate limiting is reported`() = runTest {
        val http = FakeHttp { respondJson("{}", HttpStatusCode.TooManyRequests) }
        shouldThrow<ProviderUnavailableException> { LrcmuxAPI(http.client).getSongInfo(query) }
    }

    @Test
    fun `only one match exists, so offsets above zero give null`() = runTest {
        val http = FakeHttp { respondJson(response("line", lineLines)) }
        LrcmuxAPI(http.client).getSongInfo(query, offset = 1).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `missing artist throws`() = runTest {
        shouldThrow<EmptyQueryException> {
            LrcmuxAPI(FakeHttp { notFound() }.client).getSongInfo(SongInfo("Song", ""))
        }
    }
}
