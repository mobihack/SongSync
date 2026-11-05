package pl.lambada.songsync.util

import io.kotest.matchers.shouldBe
import org.junit.Test

class WordSyncTest {

    @Test
    fun `renders a line with word timings in SongSync's word-by-word format`() {
        WordSync.render(
            listOf(
                WordSync.Line(
                    startMs = 12770,
                    words = listOf(
                        WordSync.Word("No ", 12770, 12980),
                        WordSync.Word("matter", 12980, 13370),
                    )
                )
            )
        ) shouldBe "[00:12.770]<00:12.770>No <00:12.980>matter<00:13.370>"
    }

    @Test
    fun `adds a start tag when there is a gap between words`() {
        WordSync.render(
            listOf(WordSync.Line(0, listOf(WordSync.Word("a ", 0, 100), WordSync.Word("b", 500, 700))))
        ) shouldBe "[00:00.000]<00:00.000>a <00:00.100><00:00.500>b<00:00.700>"
    }

    @Test
    fun `renders lines separated by newlines and skips lines without words`() {
        WordSync.render(
            listOf(
                WordSync.Line(1000, listOf(WordSync.Word("one", 1000, 1500))),
                WordSync.Line(2000, emptyList()),
                WordSync.Line(61000, listOf(WordSync.Word("two", 61000, 61500))),
            )
        ) shouldBe "[00:01.000]<00:01.000>one<00:01.500>\n[01:01.000]<01:01.000>two<01:01.500>"
    }

    @Test
    fun `parses NetEase yrc`() {
        WordSync.parseYrc(
            """
            {"t":0,"c":[{"tx":"作曲: "},{"tx":"Childish Gambino"}]}
            [12770,2460](12770,210,0)No (12980,390,0)matter
            [15230,1000](15230,500,0)（foo）
            """.trimIndent()
        ) shouldBe listOf(
            WordSync.Line(12770, listOf(WordSync.Word("No ", 12770, 12980), WordSync.Word("matter", 12980, 13370))),
            WordSync.Line(15230, listOf(WordSync.Word("（foo）", 15230, 15730))),
        )
    }

    @Test
    fun `empty yrc gives no lines`() {
        WordSync.parseYrc("") shouldBe emptyList()
    }
}
