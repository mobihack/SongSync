package pl.lambada.songsync.util

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsLookup
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.domain.model.SongInfo

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadLyricsTest {

    private fun song(n: Int) = Song("Song $n", "Artist", null, "/music/$n.mp3")
    private fun found(n: Int) = LyricsLookup(SongInfo("Song $n", "Artist"), "[00:01.00]la")

    private data class Counts(val success: Int, val noLyrics: Int, val failed: Int)

    private class Recorder {
        var counts = Counts(0, 0, 0)
        var completed = false
        var rateLimited = false
        val saved = mutableListOf<String?>()
    }

    private suspend fun run(
        songs: List<Song>,
        recorder: Recorder,
        find: suspend (Song) -> LyricsLookup?,
        save: suspend (Song, LyricsLookup) -> Boolean = { s, _ -> recorder.saved += s.filePath; true },
    ) = downloadLyrics(
        songs,
        findLyrics = find,
        saveLyrics = save,
        onProgressUpdate = { s, n, f -> recorder.counts = Counts(s, n, f) },
        onDownloadComplete = { recorder.completed = true },
        onRateLimitReached = { recorder.rateLimited = true },
    )

    @Test
    fun `counts saved, missing and failed songs`() = runTest {
        val r = Recorder()
        run((1..4).map(::song), r, find = { s ->
            when (s.title) {
                "Song 2" -> null
                "Song 3" -> error("provider down")
                else -> found(1)
            }
        })

        r.counts shouldBe Counts(success = 2, noLyrics = 1, failed = 1)
        r.saved shouldBe listOf("/music/1.mp3", "/music/4.mp3")
        r.completed shouldBe true
    }

    @Test
    fun `a song whose save fails counts as failed, not saved`() = runTest {
        val r = Recorder()
        run(listOf(song(1), song(2)), r, find = { found(1) }, save = { s, _ -> s.title == "Song 2" })

        r.counts shouldBe Counts(success = 1, noLyrics = 0, failed = 1)
    }

    @Test
    fun `a save that throws doesn't stop the rest of the batch`() = runTest {
        val r = Recorder()
        run((1..3).map(::song), r, find = { found(1) }, save = { s, _ ->
            if (s.title == "Song 1") throw SecurityException("read-only") else true
        })

        r.counts shouldBe Counts(success = 2, noLyrics = 0, failed = 1)
        r.completed shouldBe true
    }

    @Test
    fun `an empty batch completes straight away`() = runTest {
        val r = Recorder()
        run(emptyList(), r, find = { error("not called") })

        r.completed shouldBe true
        r.counts shouldBe Counts(0, 0, 0)
    }

    @Test
    fun `repeated lookup failures stop the batch as rate limited`() = runTest {
        val r = Recorder()
        var lookups = 0
        run((1..10).map(::song), r, find = { lookups++; error("429") })

        r.rateLimited shouldBe true
        r.completed shouldBe false
        lookups shouldBe RATE_LIMIT_FAILURES
        r.counts.failed shouldBe RATE_LIMIT_FAILURES
    }

    @Test
    fun `a successful lookup resets the rate limit count`() = runTest {
        val r = Recorder()
        run((1..9).map(::song), r, find = { s -> if (s.title == "Song 5") found(5) else error("429") })

        // 4 failures, a success, then 4 more: never 5 in a row
        r.rateLimited shouldBe false
        r.completed shouldBe true
    }

    @Test
    fun `cancelling stops the batch before the next song`() = runTest {
        val r = Recorder()
        val firstLookupStarted = CompletableDeferred<Unit>()
        val looked = mutableListOf<String?>()
        val job = launch {
            run((1..5).map(::song), r, find = { s ->
                looked += s.title
                firstLookupStarted.complete(Unit)
                CompletableDeferred<Unit>().await() // never answers
                found(1)
            })
        }
        firstLookupStarted.await()

        job.cancel()
        advanceUntilIdle()

        looked shouldBe listOf("Song 1")
        r.counts shouldBe Counts(0, 0, 0) // cancellation isn't counted as a failed song
        r.completed shouldBe false
    }
}
