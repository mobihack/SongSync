package pl.lambada.songsync.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.UnknownHostException

class ProviderChainTest {

    private val synced = "[00:01.00]Hello"
    private val plain = "Hello"

    /** Runs the chain over fake per-provider answers and records which providers were tried. */
    private suspend fun run(
        providers: List<Providers>,
        answers: Map<Providers, () -> String?>,
    ): Pair<ProviderChain.Found<String>?, List<Providers>> {
        val tried = mutableListOf<Providers>()
        val found = ProviderChain.find(providers, isSynced = ProviderChain::isSynced) { provider ->
            tried += provider
            answers[provider]?.invoke()
        }
        return found to tried
    }

    @Test
    fun `first synced result wins and later providers are not tried`() = runTest {
        val (found, tried) = run(
            listOf(Providers.LRCLIB, Providers.NETEASE, Providers.MUSIXMATCH),
            mapOf(Providers.LRCLIB to { null }, Providers.NETEASE to { synced }, Providers.MUSIXMATCH to { synced }),
        )
        found shouldBe ProviderChain.Found(Providers.NETEASE, synced)
        tried.shouldContainExactly(Providers.LRCLIB, Providers.NETEASE)
    }

    @Test
    fun `reports each provider as it is tried, with its position`() = runTest {
        val progress = mutableListOf<ProviderChain.Progress>()
        ProviderChain.find(
            listOf(Providers.LRCLIB, Providers.NETEASE, Providers.KUGOU),
            isSynced = ProviderChain::isSynced,
            onAttempt = { progress += it },
        ) { provider -> if (provider == Providers.NETEASE) synced else null }

        progress.shouldContainExactly(
            ProviderChain.Progress(Providers.LRCLIB, index = 1, total = 3),
            ProviderChain.Progress(Providers.NETEASE, index = 2, total = 3),
        )
    }

    @Test
    fun `plain lyrics are kept as a fallback while looking for synced ones`() = runTest {
        val (found, tried) = run(
            listOf(Providers.GENIUS, Providers.LRCLIB),
            mapOf(Providers.GENIUS to { plain }, Providers.LRCLIB to { synced }),
        )
        found shouldBe ProviderChain.Found(Providers.LRCLIB, synced)
        tried.shouldContainExactly(Providers.GENIUS, Providers.LRCLIB)
    }

    @Test
    fun `plain lyrics are returned when nothing synced exists`() = runTest {
        val (found, _) = run(
            listOf(Providers.LRCLIB, Providers.GENIUS, Providers.PETITLYRICS),
            mapOf(Providers.GENIUS to { plain }, Providers.PETITLYRICS to { "Other plain" }),
        )
        found shouldBe ProviderChain.Found(Providers.GENIUS, plain)
    }

    @Test
    fun `a failing provider is skipped`() = runTest {
        val (found, _) = run(
            listOf(Providers.MUSIXMATCH, Providers.LRCLIB),
            mapOf(Providers.MUSIXMATCH to { throw ProviderUnavailableException("blocked") }, Providers.LRCLIB to { synced }),
        )
        found shouldBe ProviderChain.Found(Providers.LRCLIB, synced)
    }

    @Test
    fun `nothing found anywhere gives null`() = runTest {
        val (found, tried) = run(
            listOf(Providers.LRCLIB, Providers.NETEASE),
            mapOf(Providers.NETEASE to { throw NoTrackFoundException() }),
        )
        found.shouldBeNull()
        tried.shouldContainExactly(Providers.LRCLIB, Providers.NETEASE)
    }

    @Test
    fun `with a single provider its error is reported as before`() = runTest {
        shouldThrow<ProviderUnavailableException> {
            run(listOf(Providers.MUSIXMATCH), mapOf(Providers.MUSIXMATCH to { throw ProviderUnavailableException("blocked") }))
        }
    }

    @Test
    fun `when every provider errors the last error is reported`() = runTest {
        shouldThrow<InternalErrorException> {
            run(
                listOf(Providers.MUSIXMATCH, Providers.LRCLIB),
                mapOf(
                    Providers.MUSIXMATCH to { throw ProviderUnavailableException("blocked") },
                    Providers.LRCLIB to { throw InternalErrorException("timeout") },
                ),
            )
        }
    }

    @Test
    fun `being offline stops the chain immediately`() = runTest {
        val tried = mutableListOf<Providers>()
        shouldThrow<UnknownHostException> {
            ProviderChain.find(listOf(Providers.LRCLIB, Providers.NETEASE), ProviderChain::isSynced) { provider ->
                tried += provider
                throw UnknownHostException("offline")
            }
        }
        tried.shouldContainExactly(Providers.LRCLIB)
    }

    @Test
    fun `an empty query is reported instead of trying every provider`() = runTest {
        shouldThrow<EmptyQueryException> {
            run(listOf(Providers.LRCLIB, Providers.NETEASE), mapOf(Providers.LRCLIB to { throw EmptyQueryException() }))
        }
    }

    @Test
    fun `order starts with the selected provider and skips disabled ones`() {
        val order = ProviderChain.order(selected = Providers.NETEASE, fallback = true)
        order.first() shouldBe Providers.NETEASE
        order.count { it == Providers.NETEASE } shouldBe 1
        order.none { !it.enabled } shouldBe true
        order.contains(Providers.LRCLIB) shouldBe true
    }

    @Test
    fun `without fallback only the selected provider is used`() {
        ProviderChain.order(selected = Providers.APPLE, fallback = false).shouldContainExactly(Providers.APPLE)
    }

    @Test
    fun `synced detection recognises LRC and word-by-word lyrics`() {
        ProviderChain.isSynced("[ar:Someone]\n[00:01.00]Hi") shouldBe true
        ProviderChain.isSynced("[00:01.000]<00:01.000>Hi<00:01.500>") shouldBe true
        ProviderChain.isSynced("Just text\n[Chorus]") shouldBe false
    }
}
