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

class UnisonAPITest {

    private val query = SongInfo(songName = "Let Down", artistName = "Radiohead", durationMs = 299_300)

    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:composer="https://composer.boidu.dev/ttml" composer:timing="Word"><body><div>""" +
        """<p begin="0:22.221" end="0:24.109" ttm:agent="v1"><span begin="0:22.221" end="0:23.309">Trans</span><span begin="0:23.309" end="0:24.109">port</span><span ttm:role="x-bg"><span begin="0:23.5" end="0:24.0">(ooh)</span></span></p>""" +
        """<p begin="0:24.570" end="0:26.570"><span begin="0:24.570" end="0:26.250">Motorways</span> <span begin="0:26.250" end="0:26.570">and</span></p>""" +
        """</div></body></tt>"""

    private fun response(lyrics: String, format: String) = """
        {"success":true,"data":{"id":647,"song":"Let Down","artist":"Radiohead",
          "lyrics":${JsonPrimitive(lyrics)},"format":"$format","syncType":"word",
          "score":0,"voteCount":0,"confidence":"low"}}
    """.trimIndent()

    @Test
    fun `asks for the song by title, artist and duration in seconds`() = runTest {
        val http = FakeHttp { respondJson(response(ttml, "ttml")) }
        UnisonAPI(http.client).getSongInfo(query.copy(songName = "Let Down (Remastered)"))

        val request = http.requests.single()
        request.url.toString().substringBefore('?') shouldBe "https://unison.boidu.dev/lyrics"
        request.url.parameters["song"] shouldBe "Let Down"
        request.url.parameters["artist"] shouldBe "Radiohead"
        request.url.parameters["duration"] shouldBe "299"
    }

    @Test
    fun `word-timed TTML becomes word-by-word LRC without background vocals`() = runTest {
        val song = UnisonAPI(FakeHttp { respondJson(response(ttml, "ttml")) }.client).getSongInfo(query)!!

        song.songName shouldBe "Let Down"
        song.artistName shouldBe "Radiohead"
        song.syncedLyrics shouldBe
            "[00:22.221]<00:22.221>Trans<00:23.309>port<00:24.109>\n" +
            "[00:24.570]<00:24.570>Motorways <00:26.250>and<00:26.570>"
    }

    @Test
    fun `LRC lyrics are used as they are`() = runTest {
        val lrc = "[00:22.22]Transport\n[00:24.57]Motorways and tramlines"
        val song = UnisonAPI(FakeHttp { respondJson(response(lrc, "lrc")) }.client).getSongInfo(query)!!
        song.syncedLyrics shouldBe lrc
    }

    @Test
    fun `an unknown format that isn't LRC gives null`() = runTest {
        val http = FakeHttp { respondJson(response("""{"lines":[]}""", "json")) }
        UnisonAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `not found gives null`() = runTest {
        val http = FakeHttp {
            respondJson("""{"success":false,"error":{"code":"NOT_FOUND","message":"No lyrics found"}}""", HttpStatusCode.NotFound)
        }
        UnisonAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `rate limiting is reported`() = runTest {
        val http = FakeHttp { respondJson("{}", HttpStatusCode.TooManyRequests) }
        shouldThrow<ProviderUnavailableException> { UnisonAPI(http.client).getSongInfo(query) }
    }

    @Test
    fun `only one match exists, so offsets above zero give null`() = runTest {
        val http = FakeHttp { respondJson(response(ttml, "ttml")) }
        UnisonAPI(http.client).getSongInfo(query, offset = 1).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `blank title throws`() = runTest {
        shouldThrow<EmptyQueryException> {
            UnisonAPI(FakeHttp { respondJson("{}") }.client).getSongInfo(SongInfo(" ", "Radiohead"))
        }
    }
}
