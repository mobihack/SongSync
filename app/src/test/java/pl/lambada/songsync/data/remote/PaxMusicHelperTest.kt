package pl.lambada.songsync.data.remote

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test

class PaxMusicHelperTest {

    private val helper = PaxMusicHelper()

    private fun syllable(text: String, start: Int?, end: Int?, part: Boolean = false) =
        """{"text":"$text","part":$part,"timestamp":${start ?: "null"},"endtime":${end ?: "null"}}"""

    private fun line(start: Int, vararg syllables: String) =
        """{"text":[${syllables.joinToString(",")}],"timestamp":$start,"oppositeTurn":false,
            "background":false,"backgroundText":[],"endtime":${start + 1000}}"""

    private fun response(type: String, vararg lines: String) =
        """{"type":"$type","content":[${lines.joinToString(",")}]}"""

    @Test
    fun `syllables get start and end word tags`() {
        val json = response("Syllable", line(1000, syllable("Hello", 1000, 1500), syllable("world", 1500, 2000)))

        helper.formatWordByWordLyrics(json, false) shouldBe
            "[00:01.000]<00:01.000>Hello <00:01.500>world <00:02.000>"
    }

    @Test
    fun `a syllable without an end time gets no end tag instead of null`() {
        val json = response("Syllable", line(1000, syllable("Hello", 1000, null)))

        val lrc = helper.formatWordByWordLyrics(json, false)
        lrc shouldBe "[00:01.000]<00:01.000>Hello "
        lrc shouldNotContain "null"
    }

    @Test
    fun `a syllable without a start time is kept, without a start tag`() {
        val json = response("Syllable", line(1000, syllable("Hello", null, 1500)))

        helper.formatWordByWordLyrics(json, false) shouldBe "[00:01.000]Hello <00:01.500>"
    }

    @Test
    fun `line lyrics keep an empty line instead of throwing`() {
        val json = response("Line", line(1000, syllable("First", null, null)), line(5000))

        helper.formatWordByWordLyrics(json, false) shouldBe "[00:01.000]First\n[00:05.000]"
    }

    @Test
    fun `an error page means no lyrics instead of throwing`() {
        helper.formatWordByWordLyrics("<html>502 Bad Gateway</html>", false).shouldBeNull()
    }

    @Test
    fun `no content means no lyrics`() {
        helper.formatWordByWordLyrics("""{"type":"Syllable","content":[]}""", false).shouldBeNull()
        helper.formatWordByWordLyrics("""{"type":"Syllable","content":null}""", false).shouldBeNull()
    }
}
