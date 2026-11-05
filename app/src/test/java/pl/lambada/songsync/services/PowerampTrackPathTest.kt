package pl.lambada.songsync.services

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

// Poweramp's lyrics broadcast and link are exported, so any app can send a path; only audio
// files on shared storage may be written beside or into.
class PowerampTrackPathTest {

    @Test
    fun `an audio file on internal storage is accepted`() {
        PowerampTrackPath.safeSongPath("/storage/emulated/0/Music/song.flac") shouldBe
            "/storage/emulated/0/Music/song.flac"
    }

    @Test
    fun `an audio file on an sd card is accepted, whatever the extension's case`() {
        PowerampTrackPath.safeSongPath("/storage/1A2B-3C4D/Music/Song.MP3") shouldBe
            "/storage/1A2B-3C4D/Music/Song.MP3"
    }

    @Test
    fun `the app's own data is refused`() {
        PowerampTrackPath.safeSongPath("/data/data/me.mobihack.songsyncplus/files/x.mp3").shouldBeNull()
    }

    @Test
    fun `a dot-dot escape out of shared storage is refused`() {
        PowerampTrackPath.safeSongPath("/storage/emulated/0/../../../data/x.mp3").shouldBeNull()
    }

    @Test
    fun `a dot-dot path that stays on storage comes back canonical`() {
        PowerampTrackPath.safeSongPath("/storage/emulated/0/Music/../Podcasts/ep.m4a") shouldBe
            "/storage/emulated/0/Podcasts/ep.m4a"
    }

    @Test
    fun `a file that isn't audio is refused`() {
        PowerampTrackPath.safeSongPath("/storage/emulated/0/Documents/notes.txt").shouldBeNull()
        PowerampTrackPath.safeSongPath("/storage/emulated/0/Music/song.lrc").shouldBeNull()
        PowerampTrackPath.safeSongPath("/storage/emulated/0/Music/noextension").shouldBeNull()
    }
}
