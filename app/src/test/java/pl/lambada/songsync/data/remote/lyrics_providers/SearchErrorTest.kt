package pl.lambada.songsync.data.remote.lyrics_providers

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CancellationException
import org.junit.Test
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.InternalErrorException
import pl.lambada.songsync.util.NoTrackFoundException
import java.io.IOException
import java.net.UnknownHostException

class SearchErrorTest {

    @Test
    fun `being offline passes through, so the app can show No connection`() {
        val offline = UnknownHostException("lrclib.net")
        searchError(offline) shouldBeSameInstanceAs offline
    }

    @Test
    fun `cancellation passes through, so a cancelled search stops`() {
        val cancelled = CancellationException("left the screen")
        searchError(cancelled) shouldBeSameInstanceAs cancelled
    }

    @Test
    fun `lookup outcomes pass through`() {
        listOf(NoTrackFoundException(), EmptyQueryException(), InternalErrorException("x"))
            .forEach { searchError(it) shouldBeSameInstanceAs it }
    }

    @Test
    fun `anything else becomes an internal error`() {
        val error = searchError(IOException("bad gateway"))

        error.shouldBeInstanceOf<InternalErrorException>()
        (error.message?.contains("bad gateway")) shouldBe true
    }
}
