package pl.lambada.songsync.ui.screens.home

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.domain.model.SortOrders
import pl.lambada.songsync.domain.model.SortValues
import pl.lambada.songsync.services.NotificationListener
import pl.lambada.songsync.util.FolderMode
import pl.lambada.songsync.util.MusicFolders
import pl.lambada.songsync.util.downloadLyrics
import pl.lambada.songsync.util.ext.toLrcFile
import java.io.File
import java.util.UUID

/**
 * ViewModel class for the main functionality of the app.
 */
class HomeViewModel(
    val userSettingsController: UserSettingsController,
    private val lyricsProviderService: LyricsProviderService
) : ViewModel() {
    var cachedSongs: List<Song>? = null
    val selectedSongs = mutableStateListOf<String>()

    /** O(1) membership for [selectedSongs]; list lookups are O(n) per row with thousands of songs. */
    val selectedSet by derivedStateOf { selectedSongs.toHashSet() }

    /**
     * File path of the row allowed to run the shared-element transition to the lyrics screen.
     * Only one row carries the sharedBounds modifiers, instead of every row in the list.
     */
    var transitionKey by mutableStateOf<String?>(null)
    var allSongs by mutableStateOf<List<Song>?>(null)

    var isRefreshing by mutableStateOf(false)

    var searchQuery by mutableStateOf("")

    var playingSongTitle by mutableStateOf("")
    var playingSongArtist by mutableStateOf("")
    var playingSongAlbumArt by mutableStateOf<Uri?>(null)
    var playingSongFilePath by mutableStateOf("")

    // Songs left after the folder and "no lyrics only" filters; null when nothing is filtered.
    // (An empty list is a real result, e.g. "only these folders" with none chosen.)
    private var _cachedFilteredSongs = MutableStateFlow<List<Song>?>(null)

    // searching
    private var _searchResults = MutableStateFlow<List<Song>>(emptyList())

    var displaySongs by mutableStateOf(
        when {
            searchQuery.isNotEmpty() -> _searchResults.value
            else -> _cachedFilteredSongs.value ?: allSongs ?: listOf()
        }
    )

    var showFilters by mutableStateOf(false)
    var showSort by mutableStateOf(false)
    var showingSearch by  mutableStateOf(false)
    var showSearch by mutableStateOf(showingSearch)

    val songsToBatchDownload by derivedStateOf {
        if (selectedSongs.isEmpty())
            displaySongs
        else
            (allSongs ?: listOf()).filter { it.filePath in selectedSet }
    }

    init { viewModelScope.launch { updateSongsToDisplay() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun updateSongsToDisplay() = coroutineScope {
        snapshotFlow { allSongs }
            .filterNotNull()
            // simple .combine wasn't enough apparently, so im using this
            .flatMapLatest { all ->
                _cachedFilteredSongs.combine(_searchResults) { filtered, searchResults ->
                    when {
                        searchQuery.isNotEmpty() -> searchResults
                        else -> filtered ?: all
                    }
                }
            }.collect { newDisplaySongs ->
                displaySongs = newDisplaySongs
            }
    }

    fun updateAllSongs(context: Context, sortBy: SortValues, sortOrder: SortOrders) = viewModelScope.launch(Dispatchers.IO) {
        allSongs = getAllSongs(context, sortBy, sortOrder)
    }

    /**
     * Loads all songs from the MediaStore.
     * @param context The application context.
     * @return A list of Song objects representing the songs.
     */
    private fun getAllSongs(context: Context, sortBy: SortValues, sortOrder: SortOrders): List<Song> {
        return cachedSongs ?: run {
            val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
            )
            val sortOrder = sortBy.name + " " + sortOrder.queryName

            val songs = mutableListOf<Song>()
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (it.moveToNext()) {
                    val title = it.getString(titleColumn).let { str ->
                        if (str == "<unknown>") null else str
                    }
                    val artist = it.getString(artistColumn).let { str ->
                        if (str == "<unknown>") null else str
                    }
                    val albumId = it.getLong(albumIdColumn)
                    val filePath = it.getString(pathColumn)

                    @Suppress("SpellCheckingInspection")
                    val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
                    val imgUri = ContentUris.withAppendedId(
                        sArtworkUri,
                        albumId
                    )

                    val durationMs = it.getLong(durationColumn).takeIf { ms -> ms > 0 }

                    val song = Song(title, artist, imgUri, filePath, durationMs)
                    songs.add(song)
                }
            }
            cursor?.close()
            cachedSongs = songs
            viewModelScope.launch { filterSongs() }
            viewModelScope.launch { updatePlayingSongInfo(context) }
            cachedSongs!!
        }
    }

    /**
     * Updates song search (filter) results based on the query.
     * @param query The search query.
     */
    fun updateSearchResults(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (query.isEmpty()) {
                _searchResults.value = _cachedFilteredSongs.value ?: emptyList()
                return@launch
            }

            val data: List<Song> = when {
                _cachedFilteredSongs.value != null -> _cachedFilteredSongs.value!!
                cachedSongs != null -> cachedSongs!!
                else -> { return@launch }
            }

            val results = data.filter {
                it.title?.contains(query, ignoreCase = true) == true ||
                it.artist?.contains(query, ignoreCase = true) == true
            }

            _searchResults.value = results
        }
    }

    /**
     * Applies the folder setting (all / ignore / only) and "no lyrics only" to the loaded songs.
     */
    fun filterSongs() = viewModelScope.launch(Dispatchers.Default) {
        val songs = cachedSongs ?: return@launch
        val settings = userSettingsController
        val mode = settings.folderMode
        val hideLyrics = settings.hideLyrics
        if (mode == FolderMode.ALL && !hideLyrics) {
            _cachedFilteredSongs.value = null
            return@launch
        }
        val ignored = settings.blacklistedFolders
        val only = settings.onlyFolders
        _cachedFilteredSongs.value = songs.filter { song ->
            val path = song.filePath ?: return@filter false
            MusicFolders.includes(MusicFolders.folderOf(path), mode, ignored, only) &&
                    (!hideLyrics || path.toLrcFile()?.exists() != true)
        }
    }

    fun invertSongSelection() = viewModelScope.launch {
        val selected = selectedSet
        val newSelectedSongs = displaySongs.filter { it.filePath !in selected }
        selectedSongs.clear()
        selectedSongs.addAll(newSelectedSongs.mapNotNull { it.filePath })
    }

    fun selectAllDisplayingSongs() = viewModelScope.launch {
        selectedSongs.clear()
        selectedSongs.addAll(displaySongs.fastMapNotNull { it.filePath })
    }

    fun onHideLyricsChange(newHideLyrics: Boolean) {
        userSettingsController.updateHideLyrics(newHideLyrics)
    }

    /** Finds lyrics for [query], falling back to other providers if enabled. */
    suspend fun findLyrics(query: SongInfo) = lyricsProviderService.findLyrics(
        query,
        userSettingsController.lookupProviders(),
        options = userSettingsController.lyricsOptions,
    )

    fun selectSong(song: Song, newValue: Boolean) {
        if (newValue) {
            song.filePath?.let { selectedSongs.add(it) }
            showSearch = false
            showingSearch = false
        } else {
            selectedSongs.remove(song.filePath)

            if (selectedSongs.size == 0 && searchQuery.isNotEmpty())
                showingSearch = true // show again but don't focus
        }
    }

    fun batchDownloadLyrics(
        context: Context,
        onProgressUpdate: (successCount: Int, noLyricsCount: Int, failedCount: Int) -> Unit,
        onDownloadComplete: () -> Unit,
        onRateLimitReached: () -> Unit
    ) = viewModelScope.launch {
        downloadLyrics(
            songs = songsToBatchDownload,
            viewModel = this@HomeViewModel,
            context = context,
            onProgressUpdate = onProgressUpdate,
            onDownloadComplete = onDownloadComplete,
            onRateLimitReached = onRateLimitReached,
        )
    }

    fun updatePlayingSongInfo(context: Context) = runCatching {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val controllers = msm.getActiveSessions(ComponentName(context, NotificationListener::class.java))
        val metadata = controllers[0].metadata
        playingSongTitle = metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(R.string.unknown)
        playingSongArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: context.getString(R.string.unknown)
        playingSongFilePath =  try {
            allSongs!!.filter {
                (it.title == playingSongTitle || (it.title == null && playingSongTitle == context.getString(R.string.unknown)))
                &&
                (it.artist == playingSongArtist || (it.artist == null && playingSongArtist == context.getString(R.string.unknown)))
            }[0].filePath!! + ".nowplaying" // shared transition uses path as key, add to avoid breaking stuff
        } catch (e: IndexOutOfBoundsException) {
            ""
        }
        playingSongAlbumArt = try {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)!!.let {
                // not same name of files because img won't update, cache dir cleared at app start
                val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
                it.compress(Bitmap.CompressFormat.JPEG, 100, file.outputStream())
                Uri.parse(file.absolutePath)
            }
        } catch (e: NullPointerException) {
            null
        }
    }.onFailure {
        playingSongTitle = ""
        playingSongArtist = ""
        playingSongFilePath = ""
        playingSongAlbumArt = null
    }
}