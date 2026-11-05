package pl.lambada.songsync.ui.screens.home

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import pl.lambada.songsync.domain.model.Song

class SongsToDisplayTest {

    private fun song(title: String?, artist: String? = null) = Song(title, artist, null, "/music/$title.mp3")

    private val hello = song("Hello", "Adele")
    private val yesterday = song("Yesterday", "The Beatles")
    private val untitled = song(null, "Unknown Artist")
    private val all = listOf(hello, yesterday, untitled)

    @Test
    fun `no query and no filter shows every song`() {
        songsToDisplay(all, filtered = null, query = "") shouldBe all
    }

    @Test
    fun `clearing a search that matched nothing shows every song again`() {
        songsToDisplay(all, null, "zzz").shouldBeEmpty()
        songsToDisplay(all, null, "") shouldBe all
    }

    @Test
    fun `query matches title or artist, ignoring case`() {
        songsToDisplay(all, null, "beatles") shouldBe listOf(yesterday)
        songsToDisplay(all, null, "HELLO") shouldBe listOf(hello)
        songsToDisplay(all, null, "unknown") shouldBe listOf(untitled)
    }

    @Test
    fun `a search only covers the songs left by the filters`() {
        songsToDisplay(all, filtered = listOf(hello), query = "e") shouldBe listOf(hello)
    }

    @Test
    fun `an empty filter result is shown as empty, not as every song`() {
        songsToDisplay(all, filtered = emptyList(), query = "").shouldBeEmpty()
    }
}
