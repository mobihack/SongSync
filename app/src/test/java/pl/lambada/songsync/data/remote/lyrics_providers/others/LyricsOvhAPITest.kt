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
import pl.lambada.songsync.util.EmptyQueryException

class LyricsOvhAPITest {

    private val query = SongInfo(songName = "Bohemian Rhapsody (Remastered 2011)", artistName = "Queen")

    @Test
    fun `looks lyrics up by artist and cleaned title`() = runTest {
        val http = FakeHttp { respondJson("""{"lyrics":"Is this the real life\nIs this just fantasy"}""") }
        val song = LyricsOvhAPI(http.client).getSongInfo(query)!!

        http.requests.single().url.encodedPath shouldBe "/v1/Queen/Bohemian%20Rhapsody"
        song.songName shouldBe "Bohemian Rhapsody"
        song.artistName shouldBe "Queen"
        song.unsyncedLyrics shouldBe "Is this the real life\nIs this just fantasy"
    }

    @Test
    fun `encodes characters that would break the path`() = runTest {
        val http = FakeHttp { respondJson("""{"lyrics":"x"}""") }
        LyricsOvhAPI(http.client).getSongInfo(SongInfo("What/Ever?", "AC/DC"))

        http.requests.single().url.encodedPath shouldBe "/v1/AC%2FDC/What%2FEver%3F"
    }

    @Test
    fun `not found gives null`() = runTest {
        val http = FakeHttp { respondJson("""{"error":"No lyrics found"}""", HttpStatusCode.NotFound) }
        LyricsOvhAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `blank lyrics give null`() = runTest {
        val http = FakeHttp { respondJson("""{"lyrics":"  "}""") }
        LyricsOvhAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `there is only one result, so offsets above zero give null`() = runTest {
        val http = FakeHttp { respondJson("""{"lyrics":"x"}""") }
        LyricsOvhAPI(http.client).getSongInfo(query, offset = 1).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `missing artist finds nothing, without a request`() = runTest {
        val http = FakeHttp { respondJson("{}") }
        LyricsOvhAPI(http.client).getSongInfo(SongInfo("Song", " ")).shouldBeNull()
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `missing title throws`() = runTest {
        shouldThrow<EmptyQueryException> {
            LyricsOvhAPI(FakeHttp { respondJson("{}") }.client).getSongInfo(SongInfo(" ", "Artist"))
        }
    }
}
