package pl.lambada.songsync.ui.screens.lyricsFetch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsLookup
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.ui.LocalSong
import pl.lambada.songsync.util.embedLyricsInFile
import pl.lambada.songsync.util.ext.getVersion
import pl.lambada.songsync.util.generateLrcContent
import pl.lambada.songsync.util.NoTrackFoundException
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.saveLrcFile
import pl.lambada.songsync.util.savedLyricsOf
import pl.lambada.songsync.util.showToast
import java.net.UnknownHostException

/**
 * ViewModel class for the main functionality of the app.
 */
class LyricsFetchViewModel(
    val source: LocalSong?,
    val userSettingsController: UserSettingsController,
    private val lyricsProviderService: LyricsProviderService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    /** The search or lyrics fetch running now; a new one replaces it, so an old result can't win. */
    private var fetchJob: Job? = null
    private var startedInitialSearch = false

    private fun launchFetch(block: suspend CoroutineScope.() -> Unit) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch(ioDispatcher, block = block)
    }

    /**
     * Searches for the local song the screen opened with, once: not again when the screen is
     * recreated (rotation), which would throw away the result or the query being edited.
     */
    fun loadInitialSongInfo(context: Context) {
        if (startedInitialSearch || source == null) return
        startedInitialSearch = true
        loadSongInfo(context)
    }

    var querySongName by mutableStateOf(source?.songName ?: "")
    var queryArtistName by mutableStateOf(source?.artists ?: "")

    // queryStatus: "Not submitted", "Pending", "Success", "Failed" - used to show different UI
    var queryState by mutableStateOf(
        if (source == null) QueryStatus.NotSubmitted else QueryStatus.Pending
    )
    private var queryOffset by mutableIntStateOf(0)
    var lrcOffset by mutableIntStateOf(0)

    var lyricsFetchState by mutableStateOf<LyricsFetchState>(LyricsFetchState.NotSubmitted)

    /**
     * The local song's large header has settled into its compact row: once the first search
     * ends, and for good, so later searches and a rotation don't bring it back.
     */
    var headerCollapsed by mutableStateOf(source == null)

    /** The local song's file, without the now-playing card's ".nowplaying" key suffix. */
    private val songPath = source?.filePath?.removeSuffix(".nowplaying")?.takeIf { it.isNotEmpty() }

    /** The local song already has lyrics saved: an .lrc beside it, or embedded in its tags. */
    var hasSavedLyrics by mutableStateOf(false)
        private set

    /** Checks the song's files for saved lyrics; on opening, and after saving or removing. */
    fun refreshSavedLyrics(context: Context) {
        val path = songPath ?: return
        viewModelScope.launch {
            hasSavedLyrics = withContext(ioDispatcher) { savedLyricsOf(context, path) != null }
        }
    }

    /** Removes the song's .lrc file and embedded lyrics, and says which went. */
    fun removeSavedLyrics(context: Context) {
        val path = songPath ?: return
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { pl.lambada.songsync.util.removeSavedLyrics(context, path, userSettingsController.sdCardPath) }
            }.onSuccess { removed ->
                showToast(
                    context,
                    when {
                        removed.lrcFile && removed.embedded -> R.string.lyrics_removed_both
                        removed.lrcFile -> R.string.lyrics_removed_lrc
                        else -> R.string.lyrics_removed_embedded
                    }
                )
            }.onFailure {
                showToast(context, context.getString(R.string.lyrics_remove_failed, it.message ?: context.getString(R.string.error)))
            }
            refreshSavedLyrics(context)
        }
    }

    /** Which provider a fresh search is trying right now, for showing fallback progress. */
    var searchProgress by mutableStateOf<ProviderChain.Progress?>(null)
        private set

    /** The provider that supplied the current result; can differ from the selected one. */
    var activeProvider by mutableStateOf(userSettingsController.lookupProviders().first())
        private set

    /** Providers this song's search will try, in order. */
    fun lookupProviders() = userSettingsController.lookupProviders()

    /** Next result has somewhere to go: more of this provider's results, or a provider after it. */
    fun canShowNextResult(): Boolean {
        if (pagesResults(activeProvider)) return true
        val providers = lookupProviders()
        val index = providers.indexOf(activeProvider)
        return index >= 0 && index < providers.lastIndex
    }

    /** Musixmatch's search gives one result: there's no next one to ask it for. */
    private fun pagesResults(provider: Providers) = provider != Providers.MUSIXMATCH

    private fun buildQuery() = SongInfo(
        querySongName,
        queryArtistName,
        // Only meaningful while the query still describes the local song
        durationMs = source?.durationMs?.takeIf {
            querySongName == source.songName && queryArtistName == source.artists
        }
    )

    fun loadSongInfo(context: Context, tryingAgain: Boolean = false) {
        launchFetch {
            try {
                queryState = QueryStatus.Pending
                lyricsFetchState = LyricsFetchState.NotSubmitted

                if (tryingAgain) {
                    // The next result with lyrics: this provider's next one, and once it has no
                    // more, the providers after it in the search order (auto or the user's own).
                    // Results without lyrics are passed over rather than shown one press at a time.
                    val query = buildQuery()
                    val options = userSettingsController.lyricsOptions
                    val page = ProviderChain.next(
                        providers = lookupProviders(),
                        current = activeProvider,
                        offset = queryOffset,
                        skipCurrent = !pagesResults(activeProvider),
                        onAttempt = { searchProgress = it },
                    ) { provider, offset ->
                        val match = lyricsProviderService.getSongInfo(query = query, offset = offset, provider = provider)
                        lyricsProviderService.getSyncedLyrics(match, provider, options)?.let { LyricsLookup(match, it) }
                    } ?: throw NoTrackFoundException()

                    activeProvider = page.provider
                    queryOffset = page.offset
                    searchProgress = null
                    queryState = QueryStatus.Success(page.value.songInfo)
                    lyricsFetchState = LyricsFetchState.Success(page.value.lyrics)
                } else {
                    // Fresh search: providers in the order the provider mode gives
                    queryOffset = 0
                    val providers = lookupProviders()
                    activeProvider = providers.first()
                    val found = lyricsProviderService.findLyrics(
                        buildQuery(),
                        providers,
                        options = userSettingsController.lyricsOptions,
                        onAttempt = { searchProgress = it },
                    ) ?: throw NoTrackFoundException()

                    activeProvider = found.provider
                    searchProgress = null
                    queryState = QueryStatus.Success(found.value.songInfo)
                    lyricsFetchState = LyricsFetchState.Success(found.value.lyrics)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchProgress = null
                queryState = when (e) {
                    is UnknownHostException -> QueryStatus.NoConnection
                    else -> QueryStatus.Failed(e)
                }
            }
        }
    }

    fun saveLyricsToFile(
        lyrics: String,
        song: SongInfo,
        filePath: String?,
        context: Context,
        generatedUsingString: String
    ) {
        val lrcContent = generateLrcContent(song, lyrics, generatedUsingString, lrcOffset, userSettingsController.directlyModifyTimestamps)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    saveLrcFile(context, filePath, song, lrcContent, userSettingsController.sdCardPath)
                }
            }
                .onSuccess {
                    showToast(context, R.string.file_saved_to, it.absolutePath)
                    refreshSavedLyrics(context)
                }
                .onFailure { showToast(context, it.message ?: context.getString(R.string.error)) }
        }
    }

    fun embedLyrics(
        lyrics: String,
        filePath: String?,
        context: Context,
        song: SongInfo
    ) {
        val lrcContent = generateLrcContent(song, lyrics, context.getString(R.string.generated_using), lrcOffset, userSettingsController.directlyModifyTimestamps)

        if (filePath.isNullOrEmpty()) {
            showToast(context, R.string.embed_non_local_song_error)
            return
        }

        viewModelScope.launch {
            val embedded = withContext(Dispatchers.IO) {
                embedLyricsInFile(context = context, filePath = filePath, lyrics = lrcContent)
            }
            showToast(
                context,
                if (embedded) R.string.embedded_lyrics_in_file else R.string.poweramp_embed_failed
            )
            if (embedded) refreshSavedLyrics(context)
        }
    }

    fun fetchLyricsInLanguage(songId: Long?, language: String) {
        if (songId == null) return
        
        launchFetch {
            lyricsFetchState = LyricsFetchState.Pending
            
            try {
                val lyrics = lyricsProviderService.getLyricsInLanguage(songId, language)
                if (lyrics != null) {
                    lyricsFetchState = LyricsFetchState.Success(lyrics)
                    
                    // Update the currentLanguage in the song info
                    if (queryState is QueryStatus.Success) {
                        val currentSong = (queryState as QueryStatus.Success).song
                        queryState = QueryStatus.Success(
                            currentSong.copy(currentLanguage = language)
                        )
                    }
                } else {
                    lyricsFetchState = LyricsFetchState.Failed(Exception("No lyrics found for this language"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lyricsFetchState = LyricsFetchState.Failed(e)
            }
        }
    }
}

sealed interface LyricsFetchState {
    data object NotSubmitted : LyricsFetchState
    data object Pending : LyricsFetchState
    data class Success(val lyrics: String) : LyricsFetchState
    data class Failed(val exception: Exception) : LyricsFetchState
}

sealed interface QueryStatus {
    data object NotSubmitted : QueryStatus
    data object Pending : QueryStatus
    data class Success(val song: SongInfo) : QueryStatus
    data class Failed(val exception: Exception) : QueryStatus
    data object NoConnection : QueryStatus
}