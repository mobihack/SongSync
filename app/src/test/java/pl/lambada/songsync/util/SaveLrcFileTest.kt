package pl.lambada.songsync.util

import android.content.Context
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import pl.lambada.songsync.domain.model.SongInfo
import java.io.File

class SaveLrcFileTest {

    @Test
    fun `lyrics file sits beside the song file`() {
        newLyricsFilePath("/music/Album/song.flac", SongInfo("Title", "Artist")) shouldBe
            File("/music/Album/song.lrc")
    }

    @Test
    fun `without a song file lyrics go to the SongSync+ folder`() {
        val file = newLyricsFilePath(null, SongInfo("Title", "Artist"))

        file.name shouldBe "Title - Artist.lrc"
        file.parentFile?.name shouldBe "SongSync+"
    }

    @Test
    fun `an empty song path counts as no song file`() {
        newLyricsFilePath("", SongInfo("Title", "Artist")).parentFile?.name shouldBe "SongSync+"
    }

    @Test
    fun `a slash in the artist stays in the file name`() {
        val file = newLyricsFilePath(null, SongInfo("Back In Black", "AC/DC"))

        file.name shouldBe "Back In Black - AC_DC.lrc"
        file.parentFile?.name shouldBe "SongSync+"
    }

    @Test
    fun `a title cannot climb out of the SongSync+ folder`() {
        val file = newLyricsFilePath(null, SongInfo("../../Android/x", "A"))

        file.parentFile?.name shouldBe "SongSync+"
        file.name shouldBe "_.._.._Android_x - A.lrc"
    }

    @Test
    fun `missing title and artist still give a file name`() {
        lrcFileName(SongInfo(null, null)) shouldBe " - .lrc"
    }

    @Test
    fun `sd card folders skip the leading slash`() {
        sdCardFolders("/storage/1234-ABCD/Music/Album/a.lrc", "/storage/1234-ABCD") shouldBe
            listOf("Music", "Album")
    }

    @Test
    fun `a file at the sd card root has no folders`() {
        sdCardFolders("/storage/1234-ABCD/a.lrc", "/storage/1234-ABCD") shouldBe emptyList()
    }

    private fun contextWithCacheDirs(vararg dirs: File?): Context =
        mockk { every { externalCacheDirs } returns arrayOf(*dirs) }

    @Test
    fun `saving to an sd card that isn't there says so instead of crashing`() {
        val context = contextWithCacheDirs(File("/storage/emulated/0/Android/data/app/cache"))

        shouldThrow<IllegalStateException> {
            saveLrcFile(context, "/storage/1234-ABCD/Music/a.mp3", SongInfo("T", "A"), "lrc", "content://tree")
        }.message shouldBe "No SD card found"
    }

    @Test
    fun `an unmounted sd card says so instead of crashing`() {
        val context = contextWithCacheDirs(File("/storage/emulated/0/Android/data/app/cache"), null)

        shouldThrow<IllegalStateException> {
            saveLrcFile(context, "/storage/1234-ABCD/Music/a.mp3", SongInfo("T", "A"), "lrc", "content://tree")
        }.message shouldBe "No SD card found"
    }

    @Test
    fun `saving to the sd card before choosing its folder asks for it`() {
        val context = contextWithCacheDirs(
            File("/storage/emulated/0/Android/data/app/cache"),
            File("/storage/1234-ABCD/Android/data/app/cache"),
        )

        shouldThrow<IllegalStateException> {
            saveLrcFile(context, "/storage/1234-ABCD/Music/a.mp3", SongInfo("T", "A"), "lrc", null)
        }.message shouldBe "Choose the SD card folder in settings first"
    }
}
