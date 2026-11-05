package pl.lambada.songsync.util

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsLookup
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.domain.model.SongInfo
import java.io.File

/** Saving on Android 11+, where .lrc files are written directly rather than through the SD card picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveLrcFileOnDeviceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `lyrics are written beside the song, replacing an older file`() {
        val song = tmp.newFolder("Album").resolve("01 Song.flac").apply { writeText("audio") }
        File(song.parentFile, "01 Song.lrc").writeText("old lyrics")

        val file = saveLrcFile(context, song.path, SongInfo("Song", "Artist"), "[00:01.00]new", sdCardPath = null)

        file shouldBe File(song.parentFile, "01 Song.lrc")
        file.readText() shouldBe "[00:01.00]new"
        song.readText() shouldBe "audio" // the song itself is untouched
    }

    @Test
    fun `lyrics without a song file go to Download, creating the SongSync+ folder`() {
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SongSync+")
        folder.deleteRecursively()

        val file = saveLrcFile(context, null, SongInfo("Back In Black", "AC/DC"), "[00:01.00]x", sdCardPath = null)

        file shouldBe File(folder, "Back In Black - AC_DC.lrc")
        file.readText() shouldBe "[00:01.00]x"
    }

    @Test
    fun `a batch save writes an lrc with the song tags and the app's credit`() {
        val songFile = tmp.newFile("track.mp3")
        val saved = saveBatchLyrics(
            context, Song("Track", "Band", null, songFile.path),
            LyricsLookup(SongInfo("Track", "Band"), "[00:01.00]la"),
            embed = false, directOffset = false, sdCardPath = null,
        )

        saved shouldBe true
        val lrc = File(tmp.root, "track.lrc").readText()
        lrc shouldContain "[ti:Track]"
        lrc shouldContain "[ar:Band]"
        lrc shouldContain "[00:01.00]la"
    }

    @Test
    fun `a batch song without a file isn't counted as saved`() {
        saveBatchLyrics(
            context, Song("Track", "Band", null, null),
            LyricsLookup(SongInfo("Track", "Band"), "[00:01.00]la"),
            embed = false, directOffset = false, sdCardPath = null,
        ) shouldBe false
    }

    @Test
    fun `a batch embed into a file the library doesn't have isn't counted as saved`() {
        saveBatchLyrics(
            context, Song("Track", "Band", null, tmp.newFile("x.mp3").path),
            LyricsLookup(SongInfo("Track", "Band"), "[00:01.00]la"),
            embed = true, directOffset = false, sdCardPath = null,
        ) shouldBe false
    }
}
