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

    @Test
    fun `new providers join a saved list after the provider they follow by default`() {
        val saved = listOf(Providers.APPLE, Providers.BINILYRICS, Providers.LRCLIB, Providers.GENIE)
        val known = Providers.entries.toSet() - setOf(Providers.BETTERLYRICS, Providers.UNISON)

        ProviderChain.withNewProviders(saved, known).shouldContainExactly(
            Providers.APPLE, Providers.BINILYRICS, Providers.BETTERLYRICS, Providers.LRCLIB,
            Providers.GENIE, Providers.UNISON,
        )
    }

    @Test
    fun `providers the user removed stay out, and a new one with no predecessor goes first`() {
        val known = Providers.entries.toSet() - Providers.LRCLIB
        ProviderChain.withNewProviders(listOf(Providers.NETEASE), known)
            .shouldContainExactly(Providers.LRCLIB, Providers.NETEASE)
    }

    @Test
    fun `the fallback order has the new providers in place`() {
        val order = ProviderChain.defaultOrder
        order.indexOf(Providers.BETTERLYRICS) shouldBe order.indexOf(Providers.BINILYRICS) + 1
        order.indexOf(Providers.UNISON) shouldBe order.indexOf(Providers.GENIE) + 1
    }
}
