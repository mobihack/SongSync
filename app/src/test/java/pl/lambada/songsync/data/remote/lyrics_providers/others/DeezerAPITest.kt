package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.testutil.textBody
import pl.lambada.songsync.util.ProviderUnavailableException

class DeezerAPITest {

    private val query = SongInfo(songName = "3005", artistName = "Childish Gambino")

    @Before
    fun resetSession() = DeezerAPI.clearSession()

    private fun node(id: String, title: String, artist: String, duration: Int = 234) = """
        {"node":{"id":"$id","title":"$title","duration":$duration,
          "album":{"cover":{"urls":["https://cdn/$id.jpg"]}},
          "contributors":{"edges":[
            {"node":{"name":"Featured Person"},"roles":["FEATURED"]},
            {"node":{"name":"$artist"},"roles":["MAIN"]}]}}}
    """.trimIndent()

    private fun search(vararg edges: String) =
        """{"data":{"search":{"results":{"tracks":{"edges":[${edges.joinToString(",")}]}}}}}"""

    private fun lyrics(synced: String?, text: String?) = """
        {"data":{"track":{"lyrics":{"text":${text?.let { "\"$it\"" } ?: "null"},
          "synchronizedLines":${synced ?: "null"}}}}}
    """.trimIndent()

    private fun backend(
        graphql: String,
        infoOpen: Boolean = true,
    ) = FakeHttp { request ->
        when (request.url.host) {
            "auth.deezer.com" -> respondJson("""{"jwt":"anon-jwt"}""")
            "api.deezer.com" -> respondJson("""{"country_iso":"IN","open":$infoOpen}""")
            else -> respondJson(graphql)
        }
    }

    @Test
    fun `logs in anonymously and sends the JWT with the GraphQL query`() = runTest {
        val http = backend(search(node("1", "3005", "Childish Gambino")))
        DeezerAPI(http.client).getSongInfo(query)

        http.requests[0].url.host shouldBe "auth.deezer.com"
        val gql = http.requests[1]
        gql.url.host shouldBe "pipe.deezer.com"
        gql.headers["Authorization"] shouldBe "Bearer anon-jwt"
        gql.textBody shouldContain "\"query\":\"3005 Childish Gambino\""
        gql.textBody shouldContain "\"pictureRequest\""
    }

    @Test
    fun `search strips featuring from the query`() = runTest {
        val http = backend(search(node("1", "3005", "Childish Gambino")))
        DeezerAPI(http.client).getSongInfo(SongInfo(songName = "3005 (feat. Someone)", artistName = "Childish Gambino"))

        http.requests[1].textBody shouldContain "\"query\":\"3005 Childish Gambino\""
    }

    @Test
    fun `picks the matching track and its main artist`() = runTest {
        val http = backend(search(node("9", "Something Else", "Other"), node("1", "3005", "Childish Gambino")))
        val song = DeezerAPI(http.client).getSongInfo(query)!!

        song.deezerID shouldBe 1
        song.songName shouldBe "3005"
        song.artistName shouldBe "Childish Gambino"
        song.albumCoverLink shouldBe "https://cdn/1.jpg"
        song.songLink shouldBe "https://www.deezer.com/track/1"
    }

    @Test
    fun `a result with null contributors doesn't fail the whole search`() = runTest {
        val noContributors = """{"node":{"id":"9","title":"Other","duration":200,"contributors":{"edges":null}}}"""
        val nullRoles = """{"node":{"id":"8","title":"Another","duration":200,
            "contributors":{"edges":[{"node":{"name":"X"},"roles":null}]}}}"""
        val http = backend(search(noContributors, nullRoles, node("1", "3005", "Childish Gambino")))

        DeezerAPI(http.client).getSongInfo(query)!!.deezerID shouldBe 1
    }

    @Test
    fun `unrelated results give null`() = runTest {
        val http = backend(search(node("9", "Something Else", "Other")))
        DeezerAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `blocked region is reported instead of no results`() = runTest {
        // Seen live from India: every track node is null with "Track media not found"
        val http = backend(search("null", "null"), infoOpen = false)
        val error = shouldThrow<ProviderUnavailableException> { DeezerAPI(http.client).getSongInfo(query) }
        error.message shouldContain "region"
    }

    @Test
    fun `an HTML Access Denied page is reported as blocked`() = runTest {
        val page = "<HTML><HEAD>\n<TITLE>Access Denied</TITLE>\n</HEAD><BODY>Access Denied</BODY></HTML>"
        val http = FakeHttp { request ->
            when (request.url.host) {
                "auth.deezer.com" -> respondJson("""{"jwt":"anon-jwt"}""")
                else -> respondJson(page, HttpStatusCode.Forbidden)
            }
        }
        val error = shouldThrow<ProviderUnavailableException> { DeezerAPI(http.client).getSongInfo(query) }
        error.message shouldContain "blocking"
    }

    @Test
    fun `a blocked login is reported as blocked`() = runTest {
        val http = FakeHttp { respondJson("<HTML><TITLE>Access Denied</TITLE></HTML>", HttpStatusCode.Forbidden) }
        shouldThrow<ProviderUnavailableException> { DeezerAPI(http.client).getSongInfo(query) }
    }

    @Test
    fun `null tracks in an open region just mean no results`() = runTest {
        val http = backend(search("null"), infoOpen = true)
        DeezerAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `session is reused across requests`() = runTest {
        val http = backend(search(node("1", "3005", "Childish Gambino")))
        DeezerAPI(http.client).getSongInfo(query)
        DeezerAPI(http.client).getSongInfo(query)

        http.requests.count { it.url.host == "auth.deezer.com" } shouldBe 1
    }

    @Test
    fun `expired session is renewed and the query retried`() = runTest {
        var gqlCalls = 0
        val http = FakeHttp { request ->
            when (request.url.host) {
                "auth.deezer.com" -> respondJson("""{"jwt":"anon-jwt"}""")
                else -> if (++gqlCalls == 1) respondJson("{}", HttpStatusCode.Unauthorized)
                else respondJson(search(node("1", "3005", "Childish Gambino")))
            }
        }
        DeezerAPI(http.client).getSongInfo(query)!!.deezerID shouldBe 1
        http.requests.count { it.url.host == "auth.deezer.com" } shouldBe 2
    }

    @Test
    fun `lyrics are synced LRC when available`() = runTest {
        val lines = """[{"lrcTimestamp":"[00:01.50]","line":"Hello"},{"lrcTimestamp":"[00:03.00]","line":"World"}]"""
        val http = backend(lyrics(synced = lines, text = "Hello World"))
        DeezerAPI(http.client).getSyncedLyrics(1) shouldBe "[00:01.50]Hello\n[00:03.00]World"
        http.requests[1].textBody shouldContain "\"trackId\":\"1\""
    }

    @Test
    fun `lyrics fall back to plain text`() = runTest {
        val http = backend(lyrics(synced = null, text = "Hello World"))
        DeezerAPI(http.client).getSyncedLyrics(1) shouldBe "Hello World"
    }

    @Test
    fun `no lyrics gives null`() = runTest {
        val http = backend("""{"data":{"track":{"lyrics":null}}}""")
        DeezerAPI(http.client).getSyncedLyrics(1).shouldBeNull()
    }

    @Test
    fun `rejects a track whose duration is far off`() = runTest {
        val http = backend(search(node("1", "3005", "Childish Gambino", duration = 400)))
        DeezerAPI(http.client).getSongInfo(query.copy(durationMs = 234_000)).shouldBeNull()
    }

    @Test
    fun `search asks for durations`() = runTest {
        val http = backend(search(node("1", "3005", "Childish Gambino")))
        DeezerAPI(http.client).getSongInfo(query)!!.durationMs shouldBe 234_000
    }
}
