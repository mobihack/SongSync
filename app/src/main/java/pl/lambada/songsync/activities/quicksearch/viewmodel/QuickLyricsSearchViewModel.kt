package pl.lambada.songsync.activities.quicksearch.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.util.NoTrackFoundException
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.ResourceState
import pl.lambada.songsync.util.ScreenState
import pl.lambada.songsync.util.ext.getVersion
import pl.lambada.songsync.util.parseLyrics

class QuickLyricsSearchViewModel(
    val userSettingsController: UserSettingsController,
    private val lyricsProviderService: LyricsProviderService
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuickSearchViewState())
    val state = mutableState.asStateFlow()

    data class QuickSearchViewState(
        val song: Pair<String, String>? = null, // Pair of song title and artist's name
        val screenState: ScreenState<SongInfo> = ScreenState.Loading,
        val lyricsState: ResourceState<String> = ResourceState.Loading(),
        val parsedLyrics: List<Pair<String, String>> = emptyList(),
        /** Which provider is being tried right now, for showing fallback progress. */
        val searchProgress: ProviderChain.Progress? = null,
    )

    private fun fetchSongData(song: Pair<String, String>, context: Context) {
        updateScreenState(ScreenState.Loading)

        viewModelScope.launch(Dispatchers.IO) {
            // Selected provider first, then fallbacks if enabled
            val lookup = runCatching {
                lyricsProviderService.findLyrics(
                    SongInfo(song.first, song.second),
                    userSettingsController.lookupProviders(),
                    options = userSettingsController.lyricsOptions,
                    onAttempt = { progress -> mutableState.update { it.copy(searchProgress = progress) } },
                )
            }
            mutableState.update { it.copy(searchProgress = null) }

            lookup.onFailure { exception ->
                updateScreenState(ScreenState.Error(exception))
            }
            lookup.onSuccess { found ->
                if (found == null) {
                    updateScreenState(ScreenState.Error(NoTrackFoundException()))
                    return@onSuccess
                }
                updateScreenState(ScreenState.Success(found.value.songInfo))
                updateLyricsState(ResourceState.Success(found.value.lyrics))
                mutableState.update { it.copy(parsedLyrics = parseLyrics(found.value.lyrics)) }
            }
        }
    }

    private fun updateScreenState(screenState: ScreenState<SongInfo>) {
        if (screenState != mutableState.value.screenState) {
            mutableState.update {
                it.copy(screenState = screenState)
            }
        }
    }

    private fun updateLyricsState(lyricsState: ResourceState<String>) {
        if (lyricsState != mutableState.value.lyricsState) {
            mutableState.update {
                it.copy(lyricsState = lyricsState)
            }
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.Fetch -> {
                mutableState.update {
                    it.copy(song = event.song)
                }
                fetchSongData(event.song, event.context)
            }
        }
    }

    interface Event {
        data class Fetch(val song: Pair<String, String>, val context: Context) : Event
    }
}