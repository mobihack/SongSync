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
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.ProviderUnavailableException

class GeniusAPITest {

    private val query = SongInfo(songName = "'Till I Collapse (feat. Nate Dogg)", artistName = "Eminem")

    private fun hit(id: Long, title: String, artist: String) = """
        {"result":{"id":$id,"title":"$title","url":"https://genius.com/$id",
          "header_image_thumbnail_url":"https://img/$id.jpg","primary_artist":{"name":"$artist"}}}
    """.trimIndent()

    private fun searchResponse(vararg hits: String) = """{"response":{"hits":[${hits.joinToString(",")}]}}"""

    private val songResponse = """
        {"response":{"song":{"lyrics":{"dom":{"tag":"root","children":[
          {"tag":"p","children":["'Cause sometimes you just feel tired",{"tag":"br"},
            {"tag":"a","children":["Feel weak"]},{"tag":"br"},"And when you feel weak"]}
        ]}}}}}
    """.trimIndent()

    @Test
    fun `missing token makes Genius unavailable without any request`() = runTest {
        val http = FakeHttp { respondJson(searchResponse()) }
        shouldThrow<ProviderUnavailableException> { GeniusAPI(http.client, token = "").getSongInfo(query) }
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `search sends the token and a cleaned query`() = runTest {
        val http = FakeHttp { respondJson(searchResponse(hit(1, "'Till I Collapse", "Eminem"))) }
        GeniusAPI(http.client, token = "secret").getSongInfo(query)

        val request = http.requests.single()
        request.headers["Authorization"] shouldBe "Bearer secret"
        request.url.parameters["q"] shouldBe "'Till I Collapse Eminem"
    }

    @Test
    fun `picks the matching hit instead of the first one`() = runTest {
        // Regression: the top Genius hit was "Zay's Gold Jams" by 1za1ah_l3l
        val http = FakeHttp {
            respondJson(searchResponse(hit(9, "Zay's Gold Jams", "1za1ah_l3l"), hit(1, "'Till I Collapse", "Eminem")))
        }
        val song = GeniusAPI(http.client, token = "t").getSongInfo(query)!!

        song.geniusID shouldBe 1
        song.songName shouldBe "'Till I Collapse"
        song.artistName shouldBe "Eminem"
        song.songLink shouldBe "https://genius.com/1"
        song.albumCoverLink shouldBe "https://img/1.jpg"
    }

    @Test
    fun `only unrelated hits give null`() = runTest {
        val http = FakeHttp { respondJson(searchResponse(hit(9, "Zay's Gold Jams", "1za1ah_l3l"))) }
        GeniusAPI(http.client, token = "t").getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `rejected token is reported`() = runTest {
        val http = FakeHttp { respondJson("{}", HttpStatusCode.Unauthorized) }
        shouldThrow<ProviderUnavailableException> { GeniusAPI(http.client, token = "bad").getSongInfo(query) }
    }

    @Test
    fun `lyrics DOM is flattened to text with line breaks`() = runTest {
        val http = FakeHttp { respondJson(songResponse) }
        GeniusAPI(http.client, token = "t").getLyrics(1) shouldBe
            "'Cause sometimes you just feel tired\nFeel weak\nAnd when you feel weak"
        http.requests.single().url.encodedPath shouldBe "/songs/1"
    }

    @Test
    fun `song without lyrics gives null`() = runTest {
        val http = FakeHttp { respondJson("""{"response":{"song":{"id":1}}}""") }
        GeniusAPI(http.client, token = "t").getLyrics(1).shouldBeNull()
    }
}
