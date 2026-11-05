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
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.ui.LocalSong
import pl.lambada.songsync.util.embedLyricsInFile
import pl.lambada.songsync.util.ext.getVersion
import pl.lambada.songsync.util.generateLrcContent
import pl.lambada.songsync.util.NoTrackFoundException
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.saveLrcFile
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

    /** Which provider a fresh search is trying right now, for showing fallback progress. */
    var searchProgress by mutableStateOf<ProviderChain.Progress?>(null)
        private set

    /** The provider that supplied the current result; can differ from the selected one. */
    var activeProvider by mutableStateOf(userSettingsController.lookupProviders().first())
        private set

    /** Providers this song's search will try, in order. */
    fun lookupProviders() = userSettingsController.lookupProviders()

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
                    // Next result from the provider that answered last time
                    queryOffset += 1
                    val result = lyricsProviderService
                        .getSongInfo(query = buildQuery(), offset = queryOffset, provider = activeProvider)
                        ?: error("Error fetching lyrics for the song.")

                    queryState = QueryStatus.Success(result)
                    loadLyrics(result)
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
                .onSuccess { showToast(context, R.string.file_saved_to, it.absolutePath) }
                .onFailure { showToast(context, it.message ?: context.getString(R.string.error)) }
        }
    }

    /** Runs inside the search that found [match], so replacing that search stops this too. */
    private suspend fun loadLyrics(match: SongInfo) {
        lyricsFetchState = LyricsFetchState.Pending

        try {
            val lyrics = lyricsProviderService.getSyncedLyrics(
                match,
                activeProvider,
                userSettingsController.lyricsOptions,
            ) ?: throw NullPointerException("Lyrics result is null")

            lyricsFetchState = LyricsFetchState.Success(lyrics)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lyricsFetchState = LyricsFetchState.Failed(e)
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