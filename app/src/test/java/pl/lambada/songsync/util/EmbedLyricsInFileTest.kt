package pl.lambada.songsync.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// TagLib loads a native library, so these tests stop before it is reached: they cover how the
// song file is opened and that a failure is reported, not the tag writing itself.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmbedLyricsInFileTest {

    private val mode = slot<String>()

    private fun context(openResult: () -> Nothing?): Context {
        val cursor = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
            every { getColumnIndex(any()) } returns 0
            every { getLong(0) } returns 42
        }
        val resolver = mockk<ContentResolver> {
            every { query(any(), any(), any(), any(), any()) } returns cursor
            every { openFileDescriptor(any(), capture(mode)) } answers { openResult() }
        }
        return mockk { every { contentResolver } returns resolver }
    }

    @Test
    fun `opens the song file read-write, never write-only`() {
        embedLyricsInFile(context { null }, "/storage/emulated/0/Music/a.mp3", "[00:01.00]Hi")

        mode.captured shouldBe "rw"
    }

    @Test
    fun `reports failure when the song file cannot be opened`() {
        embedLyricsInFile(context { null }, "/storage/emulated/0/Music/a.mp3", "lyrics") shouldBe false
    }

    @Test
    fun `reports failure instead of crashing when permission is denied`() {
        val ctx = context { throw SecurityException("read-only") }

        embedLyricsInFile(ctx, "/storage/emulated/0/Music/a.mp3", "lyrics") shouldBe false
    }
}
