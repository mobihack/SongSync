package pl.lambada.songsync.data.remote.lyrics_providers

import android.util.Log
import pl.lambada.songsync.data.remote.lyrics_providers.apple.AppleAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.MusixmatchAPI
import pl.lambada.songsync.data.remote.lyrics_providers.spotify.SpotifyAPI
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.EmptyQueryException
import pl.lambada.songsync.util.InternalErrorException
import pl.lambada.songsync.util.LyricsCleanup
import pl.lambada.songsync.util.NoTrackFoundException
import pl.lambada.songsync.util.ProviderUnavailableException
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.Providers
import java.io.FileNotFoundException
import java.net.UnknownHostException

/**
 * Service class for interacting with different lyrics providers.
 */
class LyricsProviderService {
    // Kept for the service's lifetime so their tokens are reused
    private val spotifyAPI = SpotifyAPI()
    private val appleAPI = AppleAPI()

    private fun sourceFor(provider: Providers): LyricsSource = when (provider) {
        Providers.SPOTIFY -> LyricsSource.Spotify(spotifyAPI)
        Providers.APPLE -> LyricsSource.Apple(appleAPI)
        Providers.LRCLIB -> LyricsSource.LrcLib
        Providers.NETEASE -> LyricsSource.Netease
        Providers.QQMUSIC -> LyricsSource.QQMusic
        Providers.MUSIXMATCH -> LyricsSource.Musixmatch
        Providers.DEEZER -> LyricsSource.Deezer
        Providers.GENIUS -> LyricsSource.Genius
        Providers.KUGOU -> LyricsSource.Kugou
        Providers.BINILYRICS -> LyricsSource.BiniLyrics
        Providers.GENIE -> LyricsSource.Genie
        Providers.PETITLYRICS -> LyricsSource.PetitLyrics
        Providers.LRCMUX -> LyricsSource.Lrcmux
        Providers.LYRICSOVH -> LyricsSource.LyricsOvh
    }

    /**
     * Refreshes the access token by sending a request to the Spotify API.
     */
    suspend fun refreshSpotifyToken() = kotlin.runCatching {
        spotifyAPI.refreshToken()
    }

    /**
     * Searches [provider] for [query].
     * @param offset (optional) The offset used for trying to find a better match or searching again.
     * @return The match, carrying the IDs [getSyncedLyrics] needs.
     */
    @Throws(
        UnknownHostException::class,
        FileNotFoundException::class,
        NoTrackFoundException::class,
        EmptyQueryException::class,
        InternalErrorException::class
    )
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0, provider: Providers): SongInfo {
        return try {
            sourceFor(provider).search(query, offset) ?: throw NoTrackFoundException()
        } catch (e: Exception) {
            when (e) {
                is InternalErrorException, is NoTrackFoundException, is EmptyQueryException,
                is ProviderUnavailableException -> throw e
                else -> throw InternalErrorException(Log.getStackTraceString(e))
            }
        }
    }

    /**
     * Gets the lyrics for [match], a result of [getSongInfo] from the same [provider].
     * @return The lyrics, LRC-formatted when synced.
     */
    suspend fun getSyncedLyrics(
        match: SongInfo,
        provider: Providers,
        options: LyricsOptions = LyricsOptions(),
    ): String? =
        // Strip credits, metadata and section labels whichever provider answered
        LyricsCleanup.clean(sourceFor(provider).lyrics(match, options))

    /**
     * Searches each of [providers] in turn (see [ProviderChain]) and returns the first match with
     * synced lyrics, or plain lyrics if no provider has synced ones.
     */
    suspend fun findLyrics(
        query: SongInfo,
        providers: List<Providers>,
        options: LyricsOptions = LyricsOptions(),
        onAttempt: (ProviderChain.Progress) -> Unit = {},
    ): ProviderChain.Found<LyricsLookup>? =
        ProviderChain.find(
            providers,
            isSynced = { ProviderChain.isSynced(it.lyrics) },
            onAttempt = onAttempt,
        ) { provider ->
            val songInfo = getSongInfo(query, provider = provider)
            val lyrics = getSyncedLyrics(songInfo, provider, options) ?: return@find null
            LyricsLookup(songInfo, lyrics)
        }

    suspend fun getLyricsInLanguage(songId: Long, language: String): String? {
        return MusixmatchAPI().getLyricsInLanguage(songId, language)
    }
}

/** A matched song together with its lyrics. */
data class LyricsLookup(val songInfo: SongInfo, val lyrics: String)
