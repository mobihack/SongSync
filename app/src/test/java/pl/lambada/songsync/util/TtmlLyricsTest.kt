package pl.lambada.songsync.util

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class TtmlLyricsTest {

    @Test
    fun `Apple word-timed TTML becomes word-by-word LRC without background vocals`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
            <p begin="0.000" end="2.906" ttm:agent="v1"><span begin="0.000" end="0.458">Is</span> <span begin="0.458" end="0.806">this</span><span ttm:role="x-bg"><span begin="1.0" end="1.5">(ooh)</span></span></p>
            <p begin="1:04.080" end="1:05.867"><span begin="1:04.080" end="1:05.867">fantasy?</span></p>
            </div></body></tt>
        """.trimIndent()

        TtmlLyrics.toLrc(ttml) shouldBe
            "[00:00.000]<00:00.000>Is <00:00.458>this<00:00.806>\n" +
            "[01:04.080]<01:04.080>fantasy?<01:05.867>"
    }

    @Test
    fun `line-timed TTML becomes line LRC`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="0.287" end="2.975">Is this the real life?</p>
            <p begin="4.080" end="5.867">Is this &amp; just fantasy?</p>
            </div></body></tt>
        """.trimIndent()

        TtmlLyrics.toLrc(ttml) shouldBe "[00:00.287]Is this the real life?\n[00:04.080]Is this & just fantasy?"
    }

    // Captured from unison.boidu.dev on 2026-11-05 ("Let Down" by Radiohead), first two lines.
    // Unison marks word timing with composer:timing and ttp:timeBase, not itunes:timing.
    @Test
    fun `Unison word-timed TTML becomes word-by-word LRC`() {
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:ttp="http://www.w3.org/ns/ttml#parameter" xmlns:composer="https://composer.boidu.dev/ttml" ttp:timeBase="media" xml:lang="en" composer:timing="Word"><head><metadata><ttm:title>Let Down (Remastered) - Radiohead</ttm:title><ttm:agent xml:id="v1" type="person"/></metadata></head><body dur="4:59.300"><div><p begin="0:22.221" end="0:24.109" ttm:agent="v1"><span begin="0:22.221" end="0:23.309">Trans</span><span begin="0:23.309" end="0:24.109">port</span></p><p begin="0:24.570" end="0:28.541" ttm:agent="v1"><span begin="0:24.570" end="0:24.818">Mo</span><span begin="0:24.818" end="0:25.399">tor</span><span begin="0:25.399" end="0:26.250">ways</span> <span begin="0:26.250" end="0:26.570">and</span> <span begin="0:26.570" end="0:28.000">tram</span><span begin="0:28.000" end="0:28.541">lines</span></p></div></body></tt>"""

        TtmlLyrics.toLrc(ttml) shouldBe
            "[00:22.221]<00:22.221>Trans<00:23.309>port<00:24.109>\n" +
            "[00:24.570]<00:24.570>Mo<00:24.818>tor<00:25.399>ways <00:26.250>and <00:26.570>tram<00:28.000>lines<00:28.541>"
    }

    @Test
    fun `TTML with a doctype is refused`() {
        TtmlLyrics.toLrc("""<?xml version="1.0"?><!DOCTYPE tt [<!ENTITY x SYSTEM "file:///etc/passwd">]><tt>&x;</tt>""")
            .shouldBeNull()
    }

    @Test
    fun `garbage and empty TTML give null`() {
        TtmlLyrics.toLrc("not xml").shouldBeNull()
        TtmlLyrics.toLrc("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div></div></body></tt>""").shouldBeNull()
    }
}
