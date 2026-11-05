package pl.lambada.songsync.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.dataStore

/**
 * Poweramp lyrics plugin (Poweramp build 948+).
 *
 * Poweramp sends [ACTION_NEED_LYRICS] with the track's details; we search the user's providers
 * (same mode and order as in the app) and answer with [ACTION_UPDATE_LYRICS]. A reply without
 * lyrics tells Poweramp the search failed, so it stops showing progress.
 *
 * See https://github.com/maxmpz/powerampapi/tree/master/poweramp_lyrics_plugin_example
 */
class PowerampLyricsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NEED_LYRICS) return

        val realId = intent.getLongExtra(EXTRA_REAL_ID, NO_ID)
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (realId == NO_ID || title.isNullOrBlank()) return

        // Unknown artist arrives as ""; streams have no duration
        val query = SongInfo(
            songName = title,
            artistName = intent.getStringExtra(EXTRA_ARTIST)?.takeIf { it.isNotBlank() },
            durationMs = intent.getIntExtra(EXTRA_DURATION_MS, 0).takeIf { it > 0 }?.toLong(),
        )

        val rawPath = intent.getStringExtra(EXTRA_PATH)
        Log.i(TAG, "Poweramp asked for \"$title\" by \"${query.artistName}\" (id=$realId, path=$rawPath)")
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val path = PowerampTrackPath.resolve(appContext, rawPath, title, query.artistName, query.durationMs)
                Log.i(TAG, "Song file for \"$title\": $path")
                val found = withTimeoutOrNull(SEARCH_TIMEOUT_MS) { findLyrics(appContext, query) }
                Log.i(
                    TAG,
                    if (found == null) "No lyrics for \"$title\" (id=$realId)"
                    else "Sent lyrics from ${found.first} for \"$title\" (id=$realId)"
                )
                if (found != null) runCatching {
                    PowerampLyricsStore.save(
                        appContext,
                        PowerampLyricsStore.Entry(realId, title, query.artistName, path, found.first, found.second),
                    )
                }
                sendLyrics(
                    appContext,
                    realId,
                    lyrics = found?.second,
                    infoLine = found?.first?.let { "Lyrics from $it via SongSync" },
                )
            } finally {
                pending.finish()
            }
        }
    }

    /** @return the provider's name and its lyrics, or null if nothing was found. */
    private suspend fun findLyrics(context: Context, query: SongInfo): Pair<String, String>? {
        val settings = UserSettingsController(context.dataStore)
        // Its own service: it keeps per-search state, and Poweramp may ask for several tracks at once
        return runCatching {
            LyricsProviderService().findLyrics(
                query,
                settings.lookupProviders(),
                includeTranslationNetEase = settings.includeTranslation,
                includeRomanizationNetEase = settings.includeRomanization,
                multiPersonWordByWord = settings.multiPersonWordByWord,
                unsyncedFallbackMusixmatch = settings.unsyncedFallbackMusixmatch,
            )
        }.onFailure { Log.w(TAG, "Lyrics search failed for \"${query.songName}\"", it) }
            .getOrNull()
            ?.let { it.provider.displayName to it.value.lyrics }
    }

    private fun sendLyrics(context: Context, realId: Long, lyrics: String?, infoLine: String?) {
        PowerampApi.send(
            context,
            Intent(ACTION_UPDATE_LYRICS)
                .putExtra(EXTRA_ID, realId)
                .putExtra(EXTRA_LYRICS, lyrics)
                .putExtra(EXTRA_INFO_LINE, infoLine),
        )
    }

    private companion object {
        const val TAG = "PowerampLyrics"

        /** Long enough to walk the provider list, short of the background broadcast limit (60s). */
        const val SEARCH_TIMEOUT_MS = 45_000L

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // From PowerampAPI (com.maxmpz.poweramp.player)
        const val ACTION_NEED_LYRICS = "com.maxmpz.audioplayer.ACTION_NEED_LYRICS"
        const val ACTION_UPDATE_LYRICS = "com.maxmpz.audioplayer.ACTION_UPDATE_LYRICS"
        const val EXTRA_ID = "id"
        const val EXTRA_LYRICS = "lyrics"
        const val EXTRA_INFO_LINE = "info_line"
        const val EXTRA_REAL_ID = "realId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_DURATION_MS = "durMs"
        const val EXTRA_PATH = "path"
        const val NO_ID = 0L
    }
}
