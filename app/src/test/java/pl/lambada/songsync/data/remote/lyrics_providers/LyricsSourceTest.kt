package pl.lambada.songsync.data.remote.lyrics_providers

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo

class LyricsSourceTest {

    private val options = LyricsOptions()

    @Test
    fun `prefetched sources return the lyrics their search already found, synced first`() = runBlocking<Unit> {
        val both = SongInfo("Song", syncedLyrics = "[00:01.00]synced", unsyncedLyrics = "plain")
        val plainOnly = SongInfo("Song", unsyncedLyrics = "plain")

        LyricsSource.Lrcmux.lyrics(both, options) shouldBe "[00:01.00]synced"
        LyricsSource.PetitLyrics.lyrics(plainOnly, options) shouldBe "plain"
        LyricsSource.LyricsOvh.lyrics(SongInfo("Song"), options) shouldBe null
    }

    @Test
    fun `a match without the source's ID has no lyrics, and nothing is requested`() = runBlocking<Unit> {
        // No IDs on the match: each source must return null before touching the network
        val bare = SongInfo("Song", "Artist")
        listOf(
            LyricsSource.LrcLib, LyricsSource.Netease, LyricsSource.QQMusic, LyricsSource.Deezer,
            LyricsSource.Genius, LyricsSource.Kugou, LyricsSource.BiniLyrics, LyricsSource.Genie,
            LyricsSource.BetterLyrics, LyricsSource.Unison,
        ).forEach { it.lyrics(bare, options) shouldBe null }
    }
}
