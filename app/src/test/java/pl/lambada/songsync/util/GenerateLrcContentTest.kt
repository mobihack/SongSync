package pl.lambada.songsync.util

import io.kotest.matchers.shouldBe
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo

class GenerateLrcContentTest {

    private val song = SongInfo(songName = "Song", artistName = "Artist")

    @Test
    fun `writes offset tag when offset is not applied directly`() {
        generateLrcContent(song, "[00:01.00]Hi", "SongSync", offset = 250, directOffset = false) shouldBe
            "[ti:Song]\n[ar:Artist]\n[offset:+250]\n[by:SongSync]\n[00:01.00]Hi"
    }

    @Test
    fun `negative offset tag has a single minus sign`() {
        generateLrcContent(song, "", "SongSync", offset = -250, directOffset = false) shouldBe
            "[ti:Song]\n[ar:Artist]\n[offset:-250]\n[by:SongSync]\n"
    }

    @Test
    fun `direct offset rewrites timestamps and omits offset tag`() {
        generateLrcContent(song, "[00:01.00]Hi", "SongSync", offset = 500, directOffset = true) shouldBe
            "[ti:Song]\n[ar:Artist]\n[by:SongSync]\n[00:01.500]Hi"
    }

    @Test
    fun `direct offset of zero leaves lyrics unchanged`() {
        generateLrcContent(song, "[00:01.00]Hi", "SongSync", offset = 0, directOffset = true) shouldBe
            "[ti:Song]\n[ar:Artist]\n[by:SongSync]\n[00:01.00]Hi"
    }
}
