package pl.lambada.songsync.activities.quicksearch

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class SharedSongTest {

    @Test
    fun `a player's own extras win over the shared text`() {
        sharedSong("Hello", "Adele", "something else") shouldBe ("Hello" to "Adele")
        sharedSong("Hello", null, null) shouldBe ("Hello" to "")
    }

    @Test
    fun `plain text with by gives title and artist`() {
        sharedSong(null, null, "Hello by Adele") shouldBe ("Hello" to "Adele")
    }

    @Test
    fun `a streaming app's share text is cleaned up`() {
        sharedSong(null, null, "Check out Hello by Adele on Spotify https://open.spotify.com/track/abc?si=1") shouldBe
            ("Hello" to "Adele")
        sharedSong(null, null, "Listen to \"Hello\" by Adele on YouTube Music") shouldBe ("Hello" to "Adele")
    }

    @Test
    fun `text without by is searched as the title`() {
        sharedSong(null, null, "Adele - Hello") shouldBe ("Adele - Hello" to "")
    }

    @Test
    fun `a bare link or empty share has nothing to search`() {
        sharedSong(null, null, "https://open.spotify.com/track/abc").shouldBeNull()
        sharedSong(null, null, "   ").shouldBeNull()
        sharedSong("  ", null, null).shouldBeNull()
    }
}
