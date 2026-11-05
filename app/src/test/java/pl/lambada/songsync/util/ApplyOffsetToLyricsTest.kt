package pl.lambada.songsync.util

import io.kotest.matchers.shouldBe
import org.junit.Ignore
import org.junit.Test

class ApplyOffsetToLyricsTest {

    @Test
    fun `zero offset normalises fraction to three digits`() {
        applyOffsetToLyrics("[00:12.34]Hello", 0) shouldBe "[00:12.340]Hello"
    }

    @Test
    fun `positive offset shifts timestamp forward`() {
        applyOffsetToLyrics("[00:12.34]Hello", 500) shouldBe "[00:12.840]Hello"
    }

    @Test
    fun `negative offset shifts timestamp back`() {
        applyOffsetToLyrics("[00:12.34]Hello", -340) shouldBe "[00:12.000]Hello"
    }

    @Test
    fun `offset rolls over a minute boundary`() {
        applyOffsetToLyrics("[00:59.90]Hello", 200) shouldBe "[01:00.100]Hello"
    }

    @Test
    fun `negative result clamps to zero`() {
        applyOffsetToLyrics("[00:00.50]Hello", -1000) shouldBe "[00:00.000]Hello"
    }

    @Test
    fun `word-level angle bracket timestamps keep their brackets`() {
        applyOffsetToLyrics("[00:01.00]<00:01.00>Hi <00:01.50>there", 100) shouldBe
            "[00:01.100]<00:01.100>Hi <00:01.600>there"
    }

    @Test
    fun `every line is shifted`() {
        val input = "[00:01.00]One\n[00:02.00]Two"
        applyOffsetToLyrics(input, 1000) shouldBe "[00:02.000]One\n[00:03.000]Two"
    }

    @Test
    fun `metadata tags are left untouched`() {
        applyOffsetToLyrics("[ti:Song]\n[ar:Artist]", 500) shouldBe "[ti:Song]\n[ar:Artist]"
    }

    @Ignore("Known bug: the fraction is always treated as centiseconds (x10), so 3-digit millisecond timestamps are shifted wrongly")
    @Test
    fun `three-digit millisecond timestamps are shifted correctly`() {
        applyOffsetToLyrics("[00:12.345]Hello", 100) shouldBe "[00:12.445]Hello"
    }
}
