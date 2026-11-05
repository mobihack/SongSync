package pl.lambada.songsync.services

import io.kotest.matchers.shouldBe
import org.junit.Test
import pl.lambada.songsync.services.LyricsRequestLimiter.Decision

class LyricsRequestLimiterTest {

    private var now = 0L
    private val limiter = LyricsRequestLimiter(maxSearches = 3, windowMs = 60_000, clock = { now })

    @Test
    fun `different tracks are searched`() {
        (1L..3L).map(limiter::check) shouldBe List(3) { Decision.SEARCH }
    }

    @Test
    fun `the same track asked again within a minute isn't searched again`() {
        limiter.check(1) shouldBe Decision.SEARCH
        now += 5_000
        limiter.check(1) shouldBe Decision.REPEAT
    }

    @Test
    fun `a flood of requests stops being searched`() {
        (1L..3L).forEach(limiter::check)
        limiter.check(4) shouldBe Decision.LIMITED
        limiter.check(5) shouldBe Decision.LIMITED
    }

    @Test
    fun `searching resumes once the minute has passed`() {
        (1L..3L).forEach(limiter::check)
        now += 60_000
        limiter.check(4) shouldBe Decision.SEARCH
        limiter.check(1) shouldBe Decision.SEARCH // no longer a repeat
    }

    @Test
    fun `a limited request doesn't use up a search`() {
        (1L..3L).forEach(limiter::check)
        repeat(10) { limiter.check(100L + it) }
        now += 60_000
        (4L..6L).map(limiter::check) shouldBe List(3) { Decision.SEARCH }
    }
}
