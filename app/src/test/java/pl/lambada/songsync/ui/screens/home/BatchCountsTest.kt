package pl.lambada.songsync.ui.screens.home

import io.kotest.matchers.shouldBe
import org.junit.Test
import pl.lambada.songsync.ui.screens.home.components.BatchCounts

class BatchCountsTest {

    @Test
    fun `done counts every outcome`() {
        BatchCounts(success = 3, noLyrics = 2, failed = 1).done shouldBe 6
    }

    @Test
    fun `percentage rounds to the nearest whole percent`() {
        BatchCounts(success = 1).percentOf(3) shouldBe 33
        BatchCounts(success = 2).percentOf(3) shouldBe 67
        BatchCounts(success = 3).percentOf(3) shouldBe 100
    }

    @Test
    fun `an empty batch is at 0 percent instead of dividing by zero`() {
        BatchCounts().percentOf(0) shouldBe 0
    }
}
