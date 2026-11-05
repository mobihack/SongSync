package pl.lambada.songsync.util.ext

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.Test
import java.io.File

class StringExtTest {

    private enum class Color { RED, GREEN }

    @Test
    fun `toEnum parses a matching name`() {
        "GREEN".toEnum(Color.RED) shouldBe Color.GREEN
    }

    @Test
    fun `toEnum falls back on unknown or null`() {
        "BLUE".toEnum(Color.RED) shouldBe Color.RED
        (null as String?).toEnum(Color.RED) shouldBe Color.RED
    }

    @Test
    fun `toLrcFile swaps the extension`() {
        "/music/song.flac".toLrcFile() shouldBe File("/music/song.lrc")
    }

    @Test
    fun `toLrcFile only replaces the last extension`() {
        "/music/a.b.mp3".toLrcFile() shouldBe File("/music/a.b.lrc")
    }

    @Test
    fun `toLrcFile appends when there is no extension`() {
        "/music/song".toLrcFile() shouldBe File("/music/song.lrc")
    }

    @Test
    fun `toLrcFile of null is null`() {
        (null as String?).toLrcFile().shouldBeNull()
    }
}
