package pl.lambada.songsync.util

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class LyricsCleanupTest {

    @Test
    fun `removes Chinese credit lines from synced lyrics`() {
        // As seen from NetEase for 不要说话
        LyricsCleanup.clean(
            """
            [00:00.00] 作词 : 小柯
            [00:01.00] 作曲 : 小柯
            [00:02.00] 编曲 : Mac Chew
            [00:18.77]深色的海面布满白色的月光
            """.trimIndent()
        ) shouldBe "[00:18.77]深色的海面布满白色的月光"
    }

    @Test
    fun `removes NetEase JSON metadata lines`() {
        LyricsCleanup.clean(
            """
            {"t":0,"c":[{"tx":"作曲: "},{"tx":"Childish Gambino"}]}
            [00:12.77]No matter what you say
            """.trimIndent()
        ) shouldBe "[00:12.77]No matter what you say"
    }

    @Test
    fun `removes backing vocal credits seen in KuGou word-by-word lyrics`() {
        LyricsCleanup.clean(
            "[00:11.250]<00:11.250>合<00:11.625>声<00:12.000>：<00:12.375>周杰伦<00:13.500>\n" +
                "[00:13.500]<00:13.500>合声编写：周杰伦<00:15.750>\n" +
                "[00:29.000]<00:29.000>故事的小黄花<00:31.500>"
        ) shouldBe "[00:29.000]<00:29.000>故事的小黄花<00:31.500>"
    }

    @Test
    fun `removes English credit lines`() {
        LyricsCleanup.clean("Written by: Someone\nLyrics by：Someone Else\nProducer: X\nHello") shouldBe "Hello"
    }

    @Test
    fun `removes section labels`() {
        LyricsCleanup.clean(
            """
            [Intro]
            Look
            [Verse 1: Eminem]
            'Cause sometimes you just feel tired
            [Pre-Chorus]
            [00:30.00][Chorus]
            """.trimIndent()
        ) shouldBe "Look\n'Cause sometimes you just feel tired"
    }

    @Test
    fun `removes credits in word-by-word lines too`() {
        LyricsCleanup.clean("[00:00.000]<00:00.000>作词 : 小柯<00:01.000>\n[00:01.000]<00:01.000>Hi<00:01.500>") shouldBe
            "[00:01.000]<00:01.000>Hi<00:01.500>"
    }

    @Test
    fun `keeps LRC tags, background vocals and ordinary lyrics`() {
        val lyrics = """
            [ar:Queen]
            [ti:Bohemian Rhapsody]
            [00:01.000]v1:<00:01.000>Is this the real life?<00:03.000>
            [bg:<00:03.000>real life<00:04.000>]
            [00:05.000]The composer: a song about him
            """.trimIndent()
        LyricsCleanup.clean(lyrics) shouldBe lyrics
    }

    @Test
    fun `collapses blank lines left behind and trims`() {
        LyricsCleanup.clean("[Verse 1]\nLine one\n\n\n[Chorus]\n\nLine two\n") shouldBe "Line one\n\nLine two"
    }

    @Test
    fun `lyrics that were only credits become null`() {
        LyricsCleanup.clean("[00:00.00] 作词 : 小柯").shouldBeNull()
    }

    @Test
    fun `null stays null`() {
        LyricsCleanup.clean(null).shouldBeNull()
    }
}
