package pl.lambada.songsync.data.remote.lyrics_providers.others

import android.util.Base64
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.testutil.FakeHttp
import pl.lambada.songsync.testutil.formFields
import pl.lambada.songsync.testutil.respondText
import pl.lambada.songsync.util.ProviderUnavailableException

class PetitLyricsAPITest {

    private val query = SongInfo(songName = "夜に駆ける (feat. Someone)", artistName = "YOASOBI")

    @Before
    fun mockBase64() {
        // android.util.Base64 isn't available in JVM unit tests; delegate to java.util.Base64
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun unmock() = unmockkStatic(Base64::class)

    private fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray())

    private fun song(id: Int, title: String, artist: String, lyrics: String) = """
        <song><lyricsId>$id</lyricsId><title>$title</title><artist>$artist</artist><album>A</album>
        <lyricsType>1</lyricsType><lyricsData>${b64(lyrics)}</lyricsData></song>
    """.trimIndent()

    private fun response(vararg songs: String) =
        """<?xml version="1.0" encoding="UTF-8"?><response><songs>${songs.joinToString("")}</songs></response>"""

    @Test
    fun `missing client id makes PetitLyrics unavailable without any request`() = runTest {
        val http = FakeHttp { respondText(response()) }
        shouldThrow<ProviderUnavailableException> { PetitLyricsAPI(http.client, clientId = "").getSongInfo(query) }
        http.requests.shouldBeEmpty()
    }

    @Test
    fun `posts a form with cleaned title, artist and client id`() = runTest {
        val http = FakeHttp { respondText(response()) }
        PetitLyricsAPI(http.client, clientId = "abc").getSongInfo(query)

        val fields = http.requests.single().formFields
        fields["key_title"] shouldBe "夜に駆ける"
        fields["key_artist"] shouldBe "YOASOBI"
        fields["clientAppId"] shouldBe "abc"
    }

    @Test
    fun `decodes the lyrics of the matching song`() = runTest {
        val http = FakeHttp {
            respondText(response(song(1, "Other Song", "Someone", "nope"), song(2, "夜に駆ける", "YOASOBI", "沈むように\n溶けてゆくように")))
        }
        val result = PetitLyricsAPI(http.client, clientId = "abc").getSongInfo(query)!!

        result.songName shouldBe "夜に駆ける"
        result.artistName shouldBe "YOASOBI"
        result.unsyncedLyrics shouldBe "沈むように\n溶けてゆくように"
        result.songLink shouldBe "https://petitlyrics.com/lyrics/2"
    }

    @Test
    fun `no results gives null`() = runTest {
        val http = FakeHttp { respondText(response()) }
        PetitLyricsAPI(http.client, clientId = "abc").getSongInfo(query).shouldBeNull()
    }

    @Test
    fun `doctype in the response is refused`() = runTest {
        // XML from a remote server must not be able to pull in external entities
        val evil = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><response>&x;</response>"""
        val http = FakeHttp { respondText(evil) }
        shouldThrow<Exception> { PetitLyricsAPI(http.client, clientId = "abc").getSongInfo(query) }
    }
}
