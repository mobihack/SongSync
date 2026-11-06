package pl.lambada.songsync.util

import kotlinx.coroutines.withTimeoutOrNull
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tries lyrics providers in order until one has synced lyrics, so a provider that is down or
 * has nothing doesn't end the lookup. Plain-text lyrics are kept as a last resort.
 */
/** How lyrics providers are chosen for a search. */
enum class ProviderMode {
    /** Every enabled provider in [ProviderChain.defaultOrder]. */
    AUTO,

    /** The user's own ordered list of providers. */
    MY_LIST,

    /** Only the selected provider. */
    SELECTED,
}

/** A choice in the provider menu: one of the two multi-provider modes, or a single provider. */
sealed interface ProviderChoice {
    data object Auto : ProviderChoice
    data object CustomOrder : ProviderChoice
    /** Selected provider mode with [provider]. */
    data class Only(val provider: Providers) : ProviderChoice
}

object ProviderChain {
    data class Found<T>(val provider: Providers, val value: T)

    /** The provider being tried right now: number [index] (1-based) of [total]. */
    data class Progress(val provider: Providers, val index: Int, val total: Int)

    /** Every provider in search order: synced sources first, plain-text ones last. */
    private val fallbackOrder = listOf(
        Providers.LRCLIB,
        Providers.NETEASE,
        Providers.LRCMUX,
        Providers.KUGOU,
        Providers.BINILYRICS,
        Providers.BETTERLYRICS,
        Providers.MUSIXMATCH,
        Providers.QQMUSIC,
        Providers.APPLE,
        Providers.DEEZER,
        Providers.GENIE,
        Providers.UNISON,
        Providers.GENIUS,
        Providers.PETITLYRICS,
        Providers.LYRICSOVH,
    )

    /** Every enabled provider, best synced sources first: Auto's order and the custom order's default. */
    val defaultOrder: List<Providers> get() = fallbackOrder.filter { it.enabled }

    /**
     * [saved] plus every enabled provider that wasn't in [known] when it was saved (one added in an
     * app update), each placed after the provider it follows in the default order. Providers the
     * user removed from the list stay out.
     */
    fun withNewProviders(saved: List<Providers>, known: Set<Providers>): List<Providers> {
        val result = saved.toMutableList()
        for (provider in defaultOrder) {
            if (provider in known || provider in result) continue
            // The nearest provider before it in the default order that the list has
            val anchor = defaultOrder.subList(0, defaultOrder.indexOf(provider)).lastOrNull { it in result }
            result.add(if (anchor == null) 0 else result.indexOf(anchor) + 1, provider)
        }
        return result
    }

    /**
     * Providers to search, in order, for the given [mode].
     *
     * @param selected the provider for [ProviderMode.SELECTED].
     * @param myList the user's ordered list, used in [ProviderMode.MY_LIST].
     */
    fun lookup(
        mode: ProviderMode,
        selected: Providers,
        myList: List<Providers>,
    ): List<Providers> = when (mode) {
        ProviderMode.AUTO -> defaultOrder
        ProviderMode.SELECTED -> listOf(selected)
        ProviderMode.MY_LIST -> myList.filter { it.enabled }.ifEmpty { defaultOrder }
    }

    private val lrcTimestamp = Regex("""^\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""", RegexOption.MULTILINE)

    fun isSynced(lyrics: String): Boolean = lrcTimestamp.containsMatchIn(lyrics)

    /**
     * Calls [attempt] for each provider in turn and returns the first synced result, or the first
     * plain one if none is synced. A provider that errors is skipped; being offline, cancellation or an empty
     * query stops the chain. With a single provider, or when every provider errored, the (last)
     * error is rethrown so callers can report it as before. An [InstrumentalException] ends the
     * search with nothing found.
     *
     * @param timeoutMs (optional) when it runs out, the providers left are skipped and the plain
     * result found so far (if any) is returned rather than nothing.
     */
    suspend fun <T> find(
        providers: List<Providers>,
        isSynced: (T) -> Boolean,
        onAttempt: (Progress) -> Unit = {},
        timeoutMs: Long? = null,
        attempt: suspend (Providers) -> T?,
    ): Found<T>? {
        var plainFallback: Found<T>? = null
        if (timeoutMs == null) return findIn(providers, isSynced, onAttempt, attempt) { plainFallback = it }
        return withTimeoutOrNull(timeoutMs) {
            // Wrapped, so a null "nothing found" can be told apart from running out of time
            Result(findIn(providers, isSynced, onAttempt, attempt) { plainFallback = it })
        }.let { finished -> if (finished != null) finished.value else plainFallback }
    }

    private class Result<T>(val value: T)

    private suspend fun <T> findIn(
        providers: List<Providers>,
        isSynced: (T) -> Boolean,
        onAttempt: (Progress) -> Unit,
        attempt: suspend (Providers) -> T?,
        onPlainFallback: (Found<T>) -> Unit,
    ): Found<T>? {
        var plainFallback: Found<T>? = null
        var lastError: Exception? = null
        var anyAnswered = false

        for ((i, provider) in providers.withIndex()) {
            onAttempt(Progress(provider, index = i + 1, total = providers.size))
            val value = try {
                attempt(provider).also { anyAnswered = true }
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoTrackFoundException) {
                // An answer, just not a match: "nothing found" rather than an error to report
                anyAnswered = true
                null
            } catch (e: EmptyQueryException) {
                throw e
            } catch (e: InstrumentalException) {
                // No lyrics anywhere: anything found so far is for another song
                return null
            } catch (e: Exception) {
                lastError = e
                null
            } ?: continue

            if (isSynced(value)) return Found(provider, value)
            if (plainFallback == null) plainFallback = Found<T>(provider, value).also(onPlainFallback)
        }

        plainFallback?.let { return it }
        val error = lastError
        if (error != null && (providers.size == 1 || !anyAnswered)) throw error
        return null
    }
}
