package pl.lambada.songsync.data.remote.lyrics_providers.others

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.notFound
import pl.lambada.songsync.testutil.respondJson
import pl.lambada.songsync.testutil.respondText

class BiniLyricsAPITest {

    private val query = SongInfo(songName = "Bohemian Rhapsody", artistName = "Queen", durationMs = 356_000)

    private val search = """
        {"results":[
          {"album_name":"Live Aid","artist_name":"Queen","duration":148,"track_name":"Bohemian Rhapsody (Live Aid)",
           "lyricsUrl":"https://lrc.red/s/LIVE.ttml","timing_type":"line"},
          {"album_name":"A Night at the Opera","artist_name":"Queen","duration":357,"track_name":"Bohemian Rhapsody",
           "lyricsUrl":"https://lrc.red/s/STUDIO.ttml","timing_type":"word"}
        ]}
    """.trimIndent()

    private val wordTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
        <p begin="0.000" end="2.906" ttm:agent="v1"><span begin="0.000" end="0.458">Is</span> <span begin="0.458" end="0.806">this</span><span ttm:role="x-bg"><span begin="1.0" end="1.5">(ooh)</span></span></p>
        <p begin="1:04.080" end="1:05.867"><span begin="1:04.080" end="1:05.867">fantasy?</span></p>
        </div></body></tt>
    """.trimIndent()

    private val lineTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
        <p begin="0.287" end="2.975">Is this the real life?</p>
        <p begin="4.080" end="5.867">Is this &amp; just fantasy?</p>
        </div></body></tt>
    """.trimIndent()

    private fun backend(ttml: String = wordTtml) = FakeHttp { request ->
        if (request.url.encodedPath.endsWith(".ttml")) respondText(ttml) else respondJson(search)
    }

    @Test
    fun `search picks the result matching title and duration`() = runTest {
        val http = backend()
        val song = BiniLyricsAPI(http.client).getSongInfo(query)!!

        song.songName shouldBe "Bohemian Rhapsody"
        song.artistName shouldBe "Queen"
        song.durationMs shouldBe 357_000
        song.songLink shouldBe "https://lrc.red/s/STUDIO.ttml"
        http.requests.single().url.parameters["q"] shouldBe "Queen Bohemian Rhapsody"
    }

    @Test
    fun `no matching result gives null`() = runTest {
        val http = FakeHttp { respondJson("""{"results":[]}""") }
        BiniLyricsAPI(http.client).getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `word-timed TTML becomes word-by-word LRC without background vocals`() = runTest {
        val http = backend(wordTtml)
        BiniLyricsAPI(http.client).getSyncedLyrics("https://lrc.red/s/STUDIO.ttml") shouldBe
            "[00:00.000]<00:00.000>Is <00:00.458>this<00:00.806>\n" +
            "[01:04.080]<01:04.080>fantasy?<01:05.867>"
    }

    @Test
    fun `line-timed TTML becomes LRC`() = runTest {
        val http = backend(lineTtml)
        BiniLyricsAPI(http.client).getSyncedLyrics("https://lrc.red/s/LIVE.ttml") shouldBe
            "[00:00.287]Is this the real life?\n[00:04.080]Is this & just fantasy?"
    }

    @Test
    fun `missing TTML gives null`() = runTest {
        BiniLyricsAPI(FakeHttp { notFound() }.client).getSyncedLyrics("https://lrc.red/s/X.ttml").shouldBeNull()
    }

    @Test
    fun `TTML with a doctype is refused`() = runTest {
        val evil = """<?xml version="1.0"?><!DOCTYPE tt [<!ENTITY x SYSTEM "file:///etc/passwd">]><tt>&x;</tt>"""
        BiniLyricsAPI(backend(evil).client).getSyncedLyrics("https://lrc.red/s/X.ttml").shouldBeNull()
    }
}
