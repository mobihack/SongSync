package pl.lambada.songsync.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class SortTest {

    @Test
    fun `saved sort settings are read back`() {
        SortOrders.fromQueryName("DESC") shouldBe SortOrders.DESCENDING
        SortValues.fromName("DATE_ADDED") shouldBe SortValues.DATE_ADDED
    }

    @Test
    fun `an unknown saved sort falls back instead of crashing every launch`() {
        SortOrders.fromQueryName("RANDOM") shouldBe SortOrders.ASCENDING
        SortValues.fromName("RATING") shouldBe SortValues.TITLE
    }

    @Test
    fun `nothing saved gives the defaults`() {
        SortOrders.fromQueryName(null) shouldBe SortOrders.ASCENDING
        SortValues.fromName(null) shouldBe SortValues.TITLE
    }
}
