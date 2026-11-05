package pl.lambada.songsync.util

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test

class SearchMatchingTest {

    private data class Hit(val title: String, val artist: String)

    private fun rank(hits: List<Hit>, title: String, artist: String?) =
        SearchMatching.rank(hits, title, artist, { it.title }, { it.artist })

    @Test
    fun `cleanTitle strips featuring and bracketed noise`() {
        SearchMatching.cleanTitle("'Till I Collapse (feat. Nate Dogg)") shouldBe "'Till I Collapse"
        SearchMatching.cleanTitle("Song [Remastered 2011]") shouldBe "Song"
        SearchMatching.cleanTitle("Song ft. Someone") shouldBe "Song"
        SearchMatching.cleanTitle("Song featuring Someone Else") shouldBe "Song"
    }

    @Test
    fun `cleanTitle keeps with in real titles`() {
        SearchMatching.cleanTitle("Stay With Me") shouldBe "Stay With Me"
    }

    @Test
    fun `cleanTitle handles null`() {
        SearchMatching.cleanTitle(null) shouldBe ""
    }

    @Test
    fun `rejects unrelated results instead of returning the first hit`() {
        // Real failure seen with Genius: the top hit had nothing to do with the song
        val hits = listOf(Hit("Zay's Gold Jams", "1za1ah_l3l"))
        rank(hits, "'Till I Collapse (feat. Nate Dogg)", "Eminem").shouldBeEmpty()
    }

    @Test
    fun `picks the matching song even when it is not ranked first`() {
        val hits = listOf(
            Hit("Zay's Gold Jams", "1za1ah_l3l"),
            Hit("'Till I Collapse (Remix)", "Some DJ"),
            Hit("'Till I Collapse", "Eminem"),
        )
        rank(hits, "'Till I Collapse (feat. Nate Dogg)", "Eminem").first() shouldBe
            Hit("'Till I Collapse", "Eminem")
    }

    @Test
    fun `matches when the local song lists several artists`() {
        val hits = listOf(Hit("Tell Me the Truth and I'll Believe It", "Sleeping At Last"))
        rank(hits, "Tell Me the Truth and I'll Believe it", "Philip Sheppard, Sleeping At Last")
            .shouldContainExactly(hits)
    }

    @Test
    fun `ignores case, accents and punctuation`() {
        SearchMatching.score("Café del Mar!", "Energy 52", "cafe del mar", "ENERGY 52") shouldBe 1.0
    }

    @Test
    fun `title alone is enough when no artist is known`() {
        SearchMatching.score("Bohemian Rhapsody", null, "Bohemian Rhapsody", "Queen") shouldBe 1.0
    }

    @Test
    fun `exact artist match outranks a cover`() {
        val original = SearchMatching.score("Hallelujah", "Leonard Cohen", "Hallelujah", "Leonard Cohen")
        val cover = SearchMatching.score("Hallelujah", "Leonard Cohen", "Hallelujah", "Jeff Buckley")
        original shouldBeGreaterThan cover
    }
}
