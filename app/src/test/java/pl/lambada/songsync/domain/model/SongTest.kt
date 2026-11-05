package pl.lambada.songsync.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class SongTest {

    @Test
    fun `search title is the tagged title`() {
        Song("Title", "Artist", null, "/music/file.mp3").searchTitle shouldBe "Title"
    }

    @Test
    fun `an untitled song searches by its file name`() {
        Song(null, null, null, "/music/Album/02 Some Song.flac").searchTitle shouldBe "02 Some Song"
    }

    @Test
    fun `an untitled song without a file has an empty search title`() {
        Song(null, null, null, null).searchTitle shouldBe ""
    }
}
