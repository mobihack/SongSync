package pl.lambada.songsync.util

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test

class ParseLyricsTest {

    @Test
    fun `parses timestamp and trimmed text per line`() {
        parseLyrics("[00:12.345] Hello \n[01:02.500]World") shouldBe listOf(
            "00:12.345" to "Hello",
            "01:02.500" to "World",
        )
    }

    @Test
    fun `skips lines without a timestamp`() {
        parseLyrics("[ti:Song]\nplain text\n\n[00:01.000]Hi") shouldBe listOf("00:01.000" to "Hi")
    }

    @Test
    fun `empty input gives empty list`() {
        parseLyrics("").shouldBeEmpty()
    }

    @Test
    fun `instrumental line keeps empty text`() {
        parseLyrics("[00:05.000]") shouldBe listOf("00:05.000" to "")
    }
}
