package pl.lambada.songsync.util

import java.net.UnknownHostException

/**
 * Tries lyrics providers in order until one has synced lyrics, so a provider that is down or
 * has nothing doesn't end the lookup. Plain-text lyrics are kept as a last resort.
 */
/** How lyrics providers are chosen for a search. */
enum class ProviderMode {
    /** The selected provider first, then every other provider. */
    AUTO,

    /** The user's own ordered list of providers. */
    MY_LIST,

    /** Only the selected provider. */
    SELECTED,
}

object ProviderChain {
    data class Found<T>(val provider: Providers, val value: T)

    /** The provider being tried right now: number [index] (1-based) of [total]. */
    data class Progress(val provider: Providers, val index: Int, val total: Int)

    /** Providers tried after the selected one: synced sources first, plain-text ones last. */
    private val fallbackOrder = listOf(
        Providers.LRCLIB,
        Providers.NETEASE,
        Providers.LRCMUX,
        Providers.KUGOU,
        Providers.BINILYRICS,
        Providers.MUSIXMATCH,
        Providers.QQMUSIC,
        Providers.APPLE,
        Providers.DEEZER,
        Providers.GENIE,
        Providers.GENIUS,
        Providers.PETITLYRICS,
        Providers.LYRICSOVH,
    )

    fun order(selected: Providers, fallback: Boolean): List<Providers> =
        if (!fallback) listOf(selected)
        else listOf(selected) + fallbackOrder.filter { it.enabled && it != selected }

    /** Default contents of "My list": every enabled provider in fallback order. */
    val defaultMyList: List<Providers> get() = fallbackOrder.filter { it.enabled }

    /**
     * Providers to search, in order, for the given [mode].
     *
     * @param selected the provider chosen in the picker (persisted).
     * @param myList the user's ordered list, used in [ProviderMode.MY_LIST].
     * @param override a temporary pick in My list mode (for the session or one song): tried first,
     * then the rest of the list.
     */
    fun lookup(
        mode: ProviderMode,
        selected: Providers,
        myList: List<Providers>,
        override: Providers? = null,
    ): List<Providers> = when (mode) {
        ProviderMode.AUTO -> order(selected, fallback = true)
        ProviderMode.SELECTED -> listOf(selected)
        ProviderMode.MY_LIST -> {
            val list = myList.filter { it.enabled }.ifEmpty { defaultMyList }
            if (override == null) list else listOf(override) + (list - override)
        }
    }

    private val lrcTimestamp = Regex("""^\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""", RegexOption.MULTILINE)

    fun isSynced(lyrics: String): Boolean = lrcTimestamp.containsMatchIn(lyrics)

    /**
     * Calls [attempt] for each provider in turn and returns the first synced result, or the first
     * plain one if none is synced. A provider that errors is skipped; being offline or an empty
     * query stops the chain. With a single provider, or when every provider errored, the (last)
     * error is rethrown so callers can report it as before.
     */
    suspend fun <T> find(
        providers: List<Providers>,
        isSynced: (T) -> Boolean,
        onAttempt: (Progress) -> Unit = {},
        attempt: suspend (Providers) -> T?,
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
            } catch (e: EmptyQueryException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                null
            } ?: continue

            if (isSynced(value)) return Found(provider, value)
            if (plainFallback == null) plainFallback = Found(provider, value)
        }

        plainFallback?.let { return it }
        val error = lastError
        if (error != null && (providers.size == 1 || !anyAnswered)) throw error
        return null
    }
}
