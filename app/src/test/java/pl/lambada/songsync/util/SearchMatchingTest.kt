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
    fun `searchText joins title and artist`() {
        SearchMatching.searchText("Hello", "Adele") shouldBe "Hello Adele"
    }

    @Test
    fun `searchText leaves out a missing or blank part`() {
        SearchMatching.searchText("Hello", null) shouldBe "Hello"
        SearchMatching.searchText(null, "Adele") shouldBe "Adele"
        SearchMatching.searchText(" Hello ", "  ") shouldBe "Hello"
    }

    @Test
    fun `searchText is blank when both are missing`() {
        SearchMatching.searchText(null, null) shouldBe ""
    }

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

    private data class Timed(val title: String, val artist: String, val durationMs: Long?)

    private fun rankTimed(hits: List<Timed>, durationMs: Long?, strict: Boolean = true) =
        SearchMatching.rank(
            hits, "Bohemian Rhapsody", "Queen",
            candidateTitle = { it.title }, candidateArtist = { it.artist },
            durationMs = durationMs, candidateDuration = { it.durationMs }, strict = strict,
        )

    @Test
    fun `prefers the version whose duration matches`() {
        val live = Timed("Bohemian Rhapsody", "Queen", 370_000)
        val studio = Timed("Bohemian Rhapsody", "Queen", 354_000)
        rankTimed(listOf(live, studio), durationMs = 355_000).first() shouldBe studio
    }

    @Test
    fun `strict mode rejects a duration far off`() {
        rankTimed(listOf(Timed("Bohemian Rhapsody", "Queen", 600_000)), durationMs = 355_000).shouldBeEmpty()
    }

    @Test
    fun `unknown durations do not affect matching`() {
        val hit = Timed("Bohemian Rhapsody", "Queen", null)
        rankTimed(listOf(hit), durationMs = 355_000).shouldContainExactly(hit)
        rankTimed(listOf(Timed("Bohemian Rhapsody", "Queen", 600_000)), durationMs = null).size shouldBe 1
    }

    @Test
    fun `soft mode reorders but never drops results`() {
        // e.g. a romanized local title vs. the provider's native-script title
        val native = Timed("夜に駆ける", "YOASOBI", 261_000)
        val exact = Timed("Bohemian Rhapsody", "Queen", 354_000)
        rankTimed(listOf(native, exact), durationMs = 354_000, strict = false)
            .shouldContainExactly(exact, native)
    }

    @Test
    fun `soft mode keeps the provider's order on ties`() {
        val a = Timed("Unrelated A", "X", null)
        val b = Timed("Unrelated B", "Y", null)
        rankTimed(listOf(a, b), durationMs = null, strict = false).shouldContainExactly(a, b)
    }

    @Test
    fun `an exact title outranks a tagged version of it`() {
        val instrumental = Hit("Dynamite (Instrumental)", "BTS")
        val original = Hit("Dynamite", "BTS")
        rank(listOf(instrumental, original), "Dynamite", "BTS").first() shouldBe original
    }

    @Test
    fun `a tagged local title still matches the plain one`() {
        rank(listOf(Hit("Song", "Artist")), "Song (Remastered 2011)", "Artist").size shouldBe 1
    }

    @Test
    fun `exact artist match outranks a cover`() {
        val original = SearchMatching.score("Hallelujah", "Leonard Cohen", "Hallelujah", "Leonard Cohen")
        val cover = SearchMatching.score("Hallelujah", "Leonard Cohen", "Hallelujah", "Jeff Buckley")
        original shouldBeGreaterThan cover
    }
}
