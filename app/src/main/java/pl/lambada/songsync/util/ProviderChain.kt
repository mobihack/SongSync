package pl.lambada.songsync.util

import java.net.UnknownHostException

/**
 * Tries lyrics providers in order until one has synced lyrics, so a provider that is down or
 * has nothing doesn't end the lookup. Plain-text lyrics are kept as a last resort.
 */
object ProviderChain {
    data class Found<T>(val provider: Providers, val value: T)

    /** Providers tried after the selected one: synced sources first, plain-text ones last. */
    private val fallbackOrder = listOf(
        Providers.LRCLIB,
        Providers.NETEASE,
        Providers.MUSIXMATCH,
        Providers.QQMUSIC,
        Providers.APPLE,
        Providers.DEEZER,
        Providers.GENIUS,
        Providers.PETITLYRICS,
    )

    fun order(selected: Providers, fallback: Boolean): List<Providers> =
        if (!fallback) listOf(selected)
        else listOf(selected) + fallbackOrder.filter { it.enabled && it != selected }

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
        attempt: suspend (Providers) -> T?,
    ): Found<T>? {
        var plainFallback: Found<T>? = null
        var lastError: Exception? = null
        var anyAnswered = false

        for (provider in providers) {
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
