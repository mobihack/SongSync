package pl.lambada.songsync.data.remote.lyrics_providers

import android.util.Log
import pl.lambada.songsync.data.remote.lyrics_providers.apple.AppleAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.DeezerAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.GeniusAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.LRCLibAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.PetitLyricsAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.MusixmatchAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.NeteaseAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.QQMusicAPI
import pl.lambada.songsync.data.remote.lyrics_providers.spotify.SpotifyAPI
import pl.lambada.songsync.data.remote.lyrics_providers.spotify.SpotifyLyricsAPI
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
    // Spotify API token
    private val spotifyAPI = SpotifyAPI()

    // Spotify Track Url
    private var spotifyUrl = ""

    // LRCLib Track ID
    private var lrcLibID = 0

    // QQMusic request payload
    private var qqPayload = ""

    // Netease Track ID and stuff
    private var neteaseID = 0L
    
    // Apple API
    private val appleAPI = AppleAPI()
    
    // Apple Track ID
    private var appleID = 0L

    // Musixmatch Song Info
    private var musixmatchSongInfo: SongInfo? = null

    // Deezer / Genius Track IDs
    private var deezerID = 0L
    private var geniusID = 0L

    // PetitLyrics returns lyrics with the search result
    private var petitLyricsSongInfo: SongInfo? = null
    // TODO: Use values from SongInfo object returned by search instead of storing them here

    /**
     * Refreshes the access token by sending a request to the Spotify API.
     */
    suspend fun refreshSpotifyToken() = kotlin.runCatching {
        spotifyAPI.refreshToken()
    }

    /**
     * Gets song information from the Spotify API.
     * @param query The SongInfo object with songName and artistName fields filled.
     * @param offset (optional) The offset used for trying to find a better match or searching again.
     * @return The SongInfo object containing the song information.
     */
    @Throws(
        UnknownHostException::class,
        FileNotFoundException::class,
        NoTrackFoundException::class,
        EmptyQueryException::class,
        InternalErrorException::class
    )
    suspend fun getSongInfo(query: SongInfo, offset: Int = 0, provider: Providers): SongInfo? {
        return try {
            when (provider) {
                Providers.SPOTIFY -> spotifyAPI.getSongInfo(query, offset).also {
                    spotifyUrl = it?.songLink ?: ""
                } ?: throw NoTrackFoundException()
                
                Providers.LRCLIB -> LRCLibAPI().getSongInfo(query, offset).also {
                    lrcLibID = it?.lrcLibID ?: 0
                } ?: throw NoTrackFoundException()

                Providers.NETEASE -> NeteaseAPI().getSongInfo(query, offset).also {
                    neteaseID = it?.neteaseID ?: 0
                } ?: throw NoTrackFoundException()

                Providers.QQMUSIC -> QQMusicAPI().getSongInfo(query, offset).also {
                    qqPayload = it?.qqPayload ?: ""
                } ?: throw NoTrackFoundException()

                Providers.APPLE -> appleAPI.getSongInfo(query, offset).also {
                    appleID = it?.appleID ?: 0
                } ?: throw NoTrackFoundException()

                Providers.MUSIXMATCH -> MusixmatchAPI().getSongInfo(query, offset).also {
                    musixmatchSongInfo = it
                } ?: throw NoTrackFoundException()

                Providers.DEEZER -> DeezerAPI().getSongInfo(query, offset).also {
                    deezerID = it?.deezerID ?: 0
                } ?: throw NoTrackFoundException()

                Providers.GENIUS -> GeniusAPI().getSongInfo(query, offset).also {
                    geniusID = it?.geniusID ?: 0
                } ?: throw NoTrackFoundException()

                Providers.PETITLYRICS -> PetitLyricsAPI().getSongInfo(query, offset).also {
                    petitLyricsSongInfo = it
                } ?: throw NoTrackFoundException()
            }
        } catch (e: Exception) {
            when (e) {
                is InternalErrorException, is NoTrackFoundException, is EmptyQueryException,
                is ProviderUnavailableException -> throw e
                else -> throw InternalErrorException(Log.getStackTraceString(e))
            }
        }
    }

    /**
     * Gets synced lyrics using the song link and returns them as a string formatted as an LRC file.
     * @param songLink The link to the song.
     * @return The synced lyrics as a string.
     */
    suspend fun getSyncedLyrics(
        songTitle: String,
        artistName: String,
        provider: Providers,
        // TODO providers could be a sealed interface to include such parameters
        includeTranslationNetEase: Boolean = false,
        includeRomanizationNetEase: Boolean = false,
        multiPersonWordByWord: Boolean = false,
        unsyncedFallbackMusixmatch: Boolean = true
    ): String? {
        // Strip credits, metadata and section labels whichever provider answered
        return LyricsCleanup.clean(when (provider) {
            Providers.SPOTIFY -> SpotifyLyricsAPI().getSyncedLyrics(spotifyUrl)
            Providers.LRCLIB -> LRCLibAPI().getSyncedLyrics(lrcLibID)
            Providers.NETEASE -> NeteaseAPI().getSyncedLyrics(
                neteaseID, includeTranslationNetEase, includeRomanizationNetEase
            )

            Providers.QQMUSIC -> QQMusicAPI().getSyncedLyrics(qqPayload, multiPersonWordByWord)

            Providers.APPLE -> appleAPI.getSyncedLyrics(
                appleID, multiPersonWordByWord
            )

            Providers.MUSIXMATCH -> MusixmatchAPI().getLyrics(
                musixmatchSongInfo,
                unsyncedFallbackMusixmatch
            )

            Providers.DEEZER -> DeezerAPI().getSyncedLyrics(deezerID)
            Providers.GENIUS -> GeniusAPI().getLyrics(geniusID)
            Providers.PETITLYRICS -> petitLyricsSongInfo?.unsyncedLyrics
        })
    }

    /**
     * Searches each of [providers] in turn (see [ProviderChain]) and returns the first match with
     * synced lyrics, or plain lyrics if no provider has synced ones.
     */
    suspend fun findLyrics(
        query: SongInfo,
        providers: List<Providers>,
        includeTranslationNetEase: Boolean = false,
        includeRomanizationNetEase: Boolean = false,
        multiPersonWordByWord: Boolean = false,
        unsyncedFallbackMusixmatch: Boolean = true,
    ): ProviderChain.Found<LyricsLookup>? =
        ProviderChain.find(providers, isSynced = { ProviderChain.isSynced(it.lyrics) }) { provider ->
            val songInfo = getSongInfo(query, provider = provider) ?: return@find null
            val lyrics = getSyncedLyrics(
                songInfo.songName.orEmpty(),
                songInfo.artistName.orEmpty(),
                provider,
                includeTranslationNetEase,
                includeRomanizationNetEase,
                multiPersonWordByWord,
                unsyncedFallbackMusixmatch,
            ) ?: return@find null
            LyricsLookup(songInfo, lyrics)
        }

    suspend fun getLyricsInLanguage(songId: Long, language: String): String? {
        return MusixmatchAPI().getLyricsInLanguage(songId, language)
    }
}

/** A matched song together with its lyrics. */
data class LyricsLookup(val songInfo: SongInfo, val lyrics: String)
