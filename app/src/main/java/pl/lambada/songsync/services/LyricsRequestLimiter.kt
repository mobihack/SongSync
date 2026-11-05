package pl.lambada.songsync.services

/**
 * Limits lyrics searches started by broadcasts. Poweramp's lyrics broadcast is exported and a
 * manifest receiver can't tell who sent it, so any app could otherwise make SongSync search every
 * provider in a loop, using up quotas and the user's IP and proxy.
 *
 * Allows at most [maxSearches] searches per [windowMs], and one per track ([key]) in that time.
 */
class LyricsRequestLimiter(
    private val maxSearches: Int = MAX_SEARCHES,
    private val windowMs: Long = WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Room for skipping quickly through a playlist (next, next, next…). */
        const val MAX_SEARCHES = 20
        const val WINDOW_MS = 60_000L
    }

    enum class Decision {
        /** Search the providers. */
        SEARCH,
        /** This track was searched moments ago: answer from that result rather than search again. */
        REPEAT,
        /** Too many searches lately: answer without searching. */
        LIMITED,
    }

    private val searches = ArrayDeque<Long>()
    private val searchedAt = HashMap<Long, Long>()

    @Synchronized
    fun check(key: Long): Decision {
        val now = clock()
        while (searches.isNotEmpty() && now - searches.first() >= windowMs) searches.removeFirst()
        searchedAt.entries.removeAll { now - it.value >= windowMs }

        if (key in searchedAt) return Decision.REPEAT
        if (searches.size >= maxSearches) return Decision.LIMITED
        searches.addLast(now)
        searchedAt[key] = now
        return Decision.SEARCH
    }
}
