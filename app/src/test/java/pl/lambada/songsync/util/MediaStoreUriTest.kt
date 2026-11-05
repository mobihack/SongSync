package pl.lambada.songsync.util

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.provider.MediaStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreUriTest {

    private val selectionArgs = slot<Array<String>>()

    private fun context(vararg ids: Long): Context {
        val cursor = MatrixCursor(arrayOf(MediaStore.Files.FileColumns._ID)).apply {
            ids.forEach { addRow(arrayOf(it)) }
        }
        val resolver = mockk<ContentResolver> {
            every { query(any(), any(), any(), capture(selectionArgs), any()) } returns cursor
        }
        return mockk { every { contentResolver } returns resolver }
    }

    @Test
    fun `a song in the library gets its own audio uri`() {
        mediaStoreUriFor(context(42), "/storage/1A2B-3C4D/Music/song.flac") shouldBe
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("42").build()
        selectionArgs.captured.toList() shouldBe listOf("/storage/1A2B-3C4D/Music/song.flac")
    }

    @Test
    fun `a song missing from the library has no uri`() {
        mediaStoreUriFor(context(), "/storage/emulated/0/Music/new.mp3").shouldBeNull()
    }
}
