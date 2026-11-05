package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.action
import pl.lambada.songsync.testutil.respondText

class GenieAPITest {

    private val query = SongInfo(songName = "Dynamite", artistName = "BTS", durationMs = 199_500)

    private fun row(id: String, title: String, artist: String) = """
        <tr class="list"  songid="$id">
          <td class="info"><a href="#" class="title ellipsis"><span class="icon icon-title">TITLE</span> $title</a>
          <a href="#" class="artist ellipsis">$artist</a><a href="#" class="albumtitle ellipsis">Album</a></td>
        </tr>
    """.trimIndent()

    private val searchPage = "<table>" +
        row("1", "Dynamite (Instrumental)", "방탄소년단") +
        row("2", "Dynamite", "방탄소년단") +
        row("3", "Dynamite &amp; More", "방탄소년단") + "</table>"

    private fun detail(duration: String) =
        """<span class="attr"><img alt="재생시간" /></span> <span class="value">$duration</span>"""

    private fun backend(durations: Map<String, String> = mapOf("1" to "03:19", "2" to "03:19", "3" to "04:40")) =
        FakeHttp { request ->
            when (request.action) {
                "searchMain" -> respondText(searchPage)
                "songInfo" -> respondText(detail(durations.getValue(request.url.parameters["xgnm"]!!)))
                else -> respondText("""null({"3050":"So watch me bring the fire","0":"Cos ah ah I'm in the stars tonight"})""")
            }
        }

    @Test
    fun `picks the best title match whose duration matches`() = runTest {
        val http = backend()
        val song = GenieAPI(http.client).getSongInfo(query)!!

        song.genieID shouldBe "2"
        song.songName shouldBe "Dynamite"
        song.artistName shouldBe "방탄소년단"
        song.durationMs shouldBe 199_000
        http.requests.first().url.parameters["query"] shouldBe "Dynamite BTS"
    }

    @Test
    fun `skips candidates whose duration is far off`() = runTest {
        val http = backend(durations = mapOf("1" to "03:19", "2" to "05:00", "3" to "04:40"))
        GenieAPI(http.client).getSongInfo(query)!!.genieID shouldBe "1"
    }

    @Test
    fun `nothing within the duration tolerance gives null`() = runTest {
        val http = backend(durations = mapOf("1" to "06:00", "2" to "06:00", "3" to "06:00"))
        GenieAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `without a known duration the best title match is used without probing`() = runTest {
        val http = backend()
        GenieAPI(http.client).getSongInfo(query.copy(durationMs = null))!!.genieID shouldBe "2"
        http.requests.none { it.action == "songInfo" } shouldBe true
    }

    @Test
    fun `strips badges and decodes entities in titles`() = runTest {
        val http = backend(durations = mapOf("1" to "06:00", "2" to "06:00", "3" to "03:19"))
        GenieAPI(http.client).getSongInfo(query)!!.songName shouldBe "Dynamite & More"
    }

    @Test
    fun `lyrics are sorted by time and formatted as LRC`() = runTest {
        val http = backend()
        GenieAPI(http.client).getSyncedLyrics("2") shouldBe
            "[00:00.000]Cos ah ah I'm in the stars tonight\n[00:03.050]So watch me bring the fire"
        http.requests.single().url.parameters["songid"] shouldBe "2"
    }

    @Test
    fun `missing lyrics give null`() = runTest {
        GenieAPI(FakeHttp { respondText("", HttpStatusCode.InternalServerError) }.client).getSyncedLyrics("2").shouldBeNull()
        GenieAPI(FakeHttp { respondText("null({})") }.client).getSyncedLyrics("2").shouldBeNull()
    }
}
