package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.ProviderUnavailableException

class BetterLyricsAPITest {

    private val query = SongInfo(songName = "Blinding Lights", artistName = "The Weeknd", durationMs = 201_570)

    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word"><body><div>""" +
        """<p begin="27.395" end="28.960" ttm:agent="v1"><span begin="27.395" end="27.549">I</span> <span begin="27.549" end="27.740">been</span></p>""" +
        """</div></body></tt>"""

    private val ok = """{"ttml":${JsonPrimitive(ttml)}}"""

    @Test
    fun `asks for the song by title, artist and duration, without an API key`() = runTest {
        val http = FakeHttp { respondJson(ok) }
        BetterLyricsAPI(http.client).getSongInfo(query)

        val request = http.requests.single()
        request.url.toString().substringBefore('?') shouldBe "https://lyrics-api.boidu.dev/getLyrics"
        request.url.parameters["s"] shouldBe "Blinding Lights"
        request.url.parameters["a"] shouldBe "The Weeknd"
        request.url.parameters["d"] shouldBe "202"
        request.headers["X-API-Key"].shouldBeNull()
    }

    @Test
    fun `TTML becomes word-by-word LRC and the query is the match`() = runTest {
        val song = BetterLyricsAPI(FakeHttp { respondJson(ok) }.client).getSongInfo(query)!!

        song.songName shouldBe "Blinding Lights"
        song.artistName shouldBe "The Weeknd"
        song.syncedLyrics shouldBe "[00:27.395]<00:27.395>I <00:27.549>been<00:27.740>"
    }

    @Test
    fun `an uncached song (401) gives null, not an error`() = runTest {
        val http = FakeHttp {
            respondJson("""{"error":"Uncached queries require a valid API key"}""", HttpStatusCode.Unauthorized)
        }
        BetterLyricsAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `rate limiting is reported`() = runTest {
        val http = FakeHttp { respondJson("{}", HttpStatusCode.TooManyRequests) }
        shouldThrow<ProviderUnavailableException> { BetterLyricsAPI(http.client).getSongInfo(query) }
    }

    @Test
    fun `only one answer exists, so offsets above zero give null`() = runTest {
        val http = FakeHttp { respondJson(ok) }
        BetterLyricsAPI(http.client).getSongInfo(query, offset = 1).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `blank title throws`() = runTest {
        shouldThrow<EmptyQueryException> {
            BetterLyricsAPI(FakeHttp { respondJson(ok) }.client).getSongInfo(SongInfo("", "The Weeknd"))
        }
    }
}
