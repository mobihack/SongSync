package pl.lambada.songsync.ui.screens.settings.providers

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test

class AutoScrollDeltaTest {

    // A 1000px viewport with 100px rows: the edge zones are the first and last 100px
    private fun delta(top: Float) = autoScrollDelta(top, top + 100f, viewportStart = 0f, viewportEnd = 1000f, edge = 100f)

    @Test
    fun `a row clear of the edges doesn't scroll`() {
        delta(top = 400f) shouldBe 0f
        delta(top = 100f) shouldBe 0f
        delta(top = 800f) shouldBe 0f
    }

    @Test
    fun `a row held near the bottom scrolls down, faster the deeper it goes`() {
        delta(top = 850f) shouldBeGreaterThan 0f
        delta(top = 950f) shouldBeGreaterThan delta(top = 850f)
    }

    @Test
    fun `a row held near the top scrolls up`() {
        delta(top = 50f) shouldBeLessThan 0f
        delta(top = -50f) shouldBeLessThan delta(top = 50f)
    }

    @Test
    fun `scrolling is capped at a quarter row per frame`() {
        delta(top = 5000f) shouldBe 25f
        delta(top = -5000f) shouldBe -25f
    }
}
