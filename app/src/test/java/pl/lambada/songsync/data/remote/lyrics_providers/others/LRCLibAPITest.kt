package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.notFound
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.util.EmptyQueryException

class LRCLibAPITest {

    private fun track(id: Int, title: String, artist: String, synced: String? = "[00:01.00]Hi") = """
        {"id":$id,"name":"$title","trackName":"$title","artistName":"$artist","albumName":"A",
         "duration":200.0,"instrumental":false,"plainLyrics":"Hi",
         "syncedLyrics":${synced?.let { "\"$it\"" } ?: "null"}}
    """.trimIndent()

    private val query = SongInfo(songName = "Bohemian Rhapsody", artistName = "Queen")

    @Test
    fun `search returns the result at the requested offset`() = runTest {
        val http = FakeHttp {
            respondJson("[${track(1, "Bohemian Rhapsody", "Queen")},${track(2, "Bohemian Rhapsody (Live)", "Queen")}]")
        }
        val api = LRCLibAPI(http.client)

        api.getSongInfo(query)!!.lrcLibID shouldBe 1
        api.getSongInfo(query, offset = 1)!!.lrcLibID shouldBe 2
        api.getSongInfo(query, offset = 2).shouldBeNull()
    }

    @Test
    fun `search sends title and artist as one query`() = runTest {
        val http = FakeHttp { respondJson("[${track(1, "Bohemian Rhapsody", "Queen")}]") }
        LRCLibAPI(http.client).getSongInfo(query)

        http.requests.single().url.encodedPath shouldBe "/api/search"
        http.requests.single().url.parameters["q"] shouldBe "Bohemian Rhapsody Queen"
    }

    @Test
    fun `a song without an artist is searched by title alone, not "null"`() = runTest {
        val http = FakeHttp { respondJson("[]") }
        LRCLibAPI(http.client).getSongInfo(SongInfo(songName = "Bohemian Rhapsody", artistName = null))

        http.requests.single().url.parameters["q"] shouldBe "Bohemian Rhapsody"
    }

    @Test
    fun `missing title and artist throws instead of searching "null null"`() = runTest {
        shouldThrow<EmptyQueryException> {
            LRCLibAPI(FakeHttp { respondJson("[]") }.client).getSongInfo(SongInfo(songName = null, artistName = null))
        }
    }

    @Test
    fun `no results gives null`() = runTest {
        val http = FakeHttp { respondJson("[]") }
        LRCLibAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `empty query throws`() = runTest {
        val http = FakeHttp { respondJson("[]") }
        shouldThrow<EmptyQueryException> {
            LRCLibAPI(http.client).getSongInfo(SongInfo(songName = "", artistName = ""))
        }
    }

    @Test
    fun `lyrics returns synced lyrics for the id`() = runTest {
        val http = FakeHttp { respondJson(track(7, "Song", "Artist", synced = "[00:01.00]Hello")) }
        LRCLibAPI(http.client).getSyncedLyrics(7) shouldBe "[00:01.00]Hello"
        http.requests.single().url.encodedPath shouldBe "/api/get/7"
    }

    @Test
    fun `lyrics without a synced version gives null`() = runTest {
        val http = FakeHttp { respondJson(track(7, "Song", "Artist", synced = null)) }
        LRCLibAPI(http.client).getSyncedLyrics(7).shouldBeNull()
    }

    @Test
    fun `lyrics for an unknown id gives null`() = runTest {
        val http = FakeHttp { notFound() }
        LRCLibAPI(http.client).getSyncedLyrics(404).shouldBeNull()
    }

    @Test
    fun `prefers the result whose duration matches the local track`() = runTest {
        val live = track(1, "Bohemian Rhapsody", "Queen").replace("200.0", "370.0")
        val studio = track(2, "Bohemian Rhapsody", "Queen").replace("200.0", "354.0")
        val http = FakeHttp { respondJson("[$live,$studio]") }

        LRCLibAPI(http.client).getSongInfo(query.copy(durationMs = 355_000))!!.lrcLibID shouldBe 2
    }

    @Test
    fun `never drops a result just because the title differs`() = runTest {
        val http = FakeHttp { respondJson("[${track(1, "夜に駆ける", "YOASOBI")}]") }
        LRCLibAPI(http.client).getSongInfo(SongInfo("Yoru ni Kakeru", "YOASOBI"))!!.lrcLibID shouldBe 1
    }
}
