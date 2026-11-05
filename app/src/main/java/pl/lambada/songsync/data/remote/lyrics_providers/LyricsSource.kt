package pl.lambada.songsync.data.remote.lyrics_providers

import pl.lambada.songsync.data.remote.lyrics_providers.apple.AppleAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.BiniLyricsAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.DeezerAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.GenieAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.GeniusAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.KugouAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.LRCLibAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.LrcmuxAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.LyricsOvhAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.MusixmatchAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.NeteaseAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.PetitLyricsAPI
import pl.lambada.songsync.data.remote.lyrics_providers.others.QQMusicAPI
import pl.lambada.songsync.data.remote.lyrics_providers.spotify.SpotifyAPI
import pl.lambada.songsync.data.remote.lyrics_providers.spotify.SpotifyLyricsAPI
import pl.lambada.songsync.domain.model.SongInfo

/** Settings that change what some providers return. Each [LyricsSource] reads only the ones it supports. */
data class LyricsOptions(
    /** Netease: add the translated line under each line. */
    val includeTranslation: Boolean = false,
    /** Netease: add the romanized line under each line. */
    val includeRomanization: Boolean = false,
    /** Apple Music, QQ Music: keep a separate voice per singer in word-by-word lyrics. */
    val multiPersonWordByWord: Boolean = false,
    /** Musixmatch: return plain lyrics when there are no synced ones. */
    val unsyncedFallback: Boolean = true,
)

/**
 * One lyrics provider: [search] finds a match, and [lyrics] fetches its lyrics using only the IDs
 * that [search] put on that match, so a source keeps no state between songs.
 */
sealed interface LyricsSource {
    suspend fun search(query: SongInfo, offset: Int): SongInfo?
    suspend fun lyrics(match: SongInfo, options: LyricsOptions): String?

    class Spotify(private val api: SpotifyAPI) : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = api.getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.songLink?.let { SpotifyLyricsAPI().getSyncedLyrics(it) }
    }

    class Apple(private val api: AppleAPI) : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = api.getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.appleID?.let { api.getSyncedLyrics(it, options.multiPersonWordByWord) }
    }

    data object LrcLib : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = LRCLibAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.lrcLibID?.let { LRCLibAPI().getSyncedLyrics(it) }
    }

    data object Netease : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = NeteaseAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.neteaseID?.let {
                NeteaseAPI().getSyncedLyrics(it, options.includeTranslation, options.includeRomanization)
            }
    }

    data object QQMusic : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = QQMusicAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.qqPayload?.let { QQMusicAPI().getSyncedLyrics(it, options.multiPersonWordByWord) }
    }

    data object Musixmatch : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = MusixmatchAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            MusixmatchAPI().getLyrics(match, options.unsyncedFallback)
    }

    data object Deezer : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = DeezerAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.deezerID?.let { DeezerAPI().getSyncedLyrics(it) }
    }

    data object Genius : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = GeniusAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.geniusID?.let { GeniusAPI().getLyrics(it) }
    }

    data object Kugou : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = KugouAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.kugouHash?.let { KugouAPI().getSyncedLyrics(it, match.durationMs) }
    }

    data object BiniLyrics : LyricsSource {
        // The match's songLink is the URL of its TTML file
        override suspend fun search(query: SongInfo, offset: Int) = BiniLyricsAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.songLink?.let { BiniLyricsAPI().getSyncedLyrics(it) }
    }

    data object Genie : LyricsSource {
        override suspend fun search(query: SongInfo, offset: Int) = GenieAPI().getSongInfo(query, offset)
        override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.genieID?.let { GenieAPI().getSyncedLyrics(it) }
    }

    /** Providers whose search result already carries the lyrics. */
    sealed class Prefetched : LyricsSource {
        final override suspend fun lyrics(match: SongInfo, options: LyricsOptions) =
            match.syncedLyrics ?: match.unsyncedLyrics
    }

    data object PetitLyrics : Prefetched() {
        override suspend fun search(query: SongInfo, offset: Int) = PetitLyricsAPI().getSongInfo(query, offset)
    }

    data object Lrcmux : Prefetched() {
        override suspend fun search(query: SongInfo, offset: Int) = LrcmuxAPI().getSongInfo(query, offset)
    }

    data object LyricsOvh : Prefetched() {
        override suspend fun search(query: SongInfo, offset: Int) = LyricsOvhAPI().getSongInfo(query, offset)
    }
}
