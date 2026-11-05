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

    @Test
    fun `hundredths are read as hundredths, not milliseconds`() {
        parseLyrics("[00:12.34]Hello") shouldBe listOf("00:12.340" to "Hello")
    }

    @Test
    fun `a timestamp without a fraction gets zero milliseconds`() {
        parseLyrics("[00:12]Hello") shouldBe listOf("00:12.000" to "Hello")
    }

    @Test
    fun `leading whitespace before the timestamp is ignored`() {
        parseLyrics("  [00:01.000]Hi") shouldBe listOf("00:01.000" to "Hi")
    }

    @Test
    fun `a line with several timestamps appears at each, in time order`() {
        parseLyrics("[00:01.00][00:30.00]Chorus\n[00:10.00]Verse") shouldBe listOf(
            "00:01.000" to "Chorus",
            "00:10.000" to "Verse",
            "00:30.000" to "Chorus",
        )
    }

    @Test
    fun `word-by-word tags and speaker marks are left out of the text`() {
        parseLyrics("[00:01.000]v1:<00:01.000>Hello <00:01.500>world <00:02.000>") shouldBe
            listOf("00:01.000" to "Hello world")
    }
}
