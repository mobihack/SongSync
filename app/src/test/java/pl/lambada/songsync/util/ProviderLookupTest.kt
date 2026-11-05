package pl.lambada.songsync.util

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class ProviderLookupTest {

    private val myList = listOf(Providers.NETEASE, Providers.LRCLIB, Providers.APPLE)

    @Test
    fun `auto tries every enabled provider in the default order, whatever is selected`() {
        val order = ProviderChain.lookup(ProviderMode.AUTO, Providers.APPLE, myList)
        order shouldBe ProviderChain.defaultOrder
        order.first() shouldBe Providers.LRCLIB
        order.all { it.enabled } shouldBe true
    }

    @Test
    fun `selected mode only tries the selected provider`() {
        ProviderChain.lookup(ProviderMode.SELECTED, Providers.KUGOU, myList)
            .shouldContainExactly(Providers.KUGOU)
    }

    @Test
    fun `my list follows the user's order and ignores the selected provider`() {
        ProviderChain.lookup(ProviderMode.MY_LIST, Providers.KUGOU, myList)
            .shouldContainExactly(Providers.NETEASE, Providers.LRCLIB, Providers.APPLE)
    }

    @Test
    fun `disabled providers are dropped from my list, and an empty list falls back to the default`() {
        ProviderChain.lookup(ProviderMode.MY_LIST, Providers.LRCLIB, listOf(Providers.SPOTIFY, Providers.LRCLIB))
            .shouldContainExactly(Providers.LRCLIB)
        ProviderChain.lookup(ProviderMode.MY_LIST, Providers.LRCLIB, emptyList()) shouldBe ProviderChain.defaultOrder
    }
}
