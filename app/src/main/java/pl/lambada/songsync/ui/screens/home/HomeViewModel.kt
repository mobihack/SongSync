package pl.lambada.songsync.ui.screens.home

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import pl.lambada.songsync.util.saveBatchLyrics
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

    /** Whether to show the now-playing song: always, unless a search is on and it doesn't match. */
    val showNowPlaying: Boolean
        get() = playingSongTitle.isNotEmpty() && (
            searchQuery.isEmpty() ||
                playingSongTitle.contains(searchQuery, ignoreCase = true) ||
                playingSongArtist.contains(searchQuery, ignoreCase = true)
            )

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

    private var batchJob: Job? = null

    /** Starts a batch download of [songsToBatchDownload], replacing any one still running. */
    fun batchDownloadLyrics(
        context: Context,
        onProgressUpdate: (successCount: Int, noLyricsCount: Int, failedCount: Int) -> Unit,
        onDownloadComplete: () -> Unit,
        onRateLimitReached: () -> Unit
    ) {
        batchJob?.cancel()
        val appContext = context.applicationContext
        val settings = userSettingsController
        batchJob = viewModelScope.launch {
            downloadLyrics(
                songs = songsToBatchDownload,
                findLyrics = { song ->
                    findLyrics(SongInfo(song.title, song.artist, durationMs = song.durationMs))?.value
                },
                saveLyrics = { song, lyrics ->
                    withContext(Dispatchers.IO) {
                        saveBatchLyrics(
                            appContext, song, lyrics,
                            embed = settings.embedLyricsIntoFiles,
                            directOffset = settings.directlyModifyTimestamps,
                            sdCardPath = settings.sdCardPath,
                        )
                    }
                },
                onProgressUpdate = onProgressUpdate,
                onDownloadComplete = onDownloadComplete,
                onRateLimitReached = onRateLimitReached,
            )
        }
    }

    /** Stops the running batch download, if any; songs already saved stay saved. */
    fun cancelBatchDownload() {
        batchJob?.cancel()
        batchJob = null
    }

    // Live now-playing tracking: every active media session is watched, so pausing, skipping or
    // another player starting updates the card while the screen is open.
    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var watchedControllers: List<MediaController> = emptyList()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pickAndRead()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pickAndRead()
        override fun onSessionDestroyed() = pickAndRead()
    }
    /** Title, artist and player of the song whose album art is cached, so art isn't rewritten on every pause. */
    private var artKey: String? = null
    private var artClearJob: Job? = null

    /** Whether the now-playing song is playing right now, rather than paused (shown as "Last played"). */
    var playingSongIsPlaying by mutableStateOf(false)

    /** Which way the last song change went: 1 for next, -1 for previous. Drives the card's slide. */
    var playingSongDirection by mutableIntStateOf(1)
    private var currentTrackKey: String? = null
    private var previousTrackKey: String? = null
    /** Set when a player reports it is skipping, for the song change that follows. */
    private var pendingSkip = 0

    fun updatePlayingSongInfo(context: Context) {
        val app = context.applicationContext
        appContext = app
        runCatching {
            val msm = sessionManager
                ?: (app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { sessionManager = it }
            val component = ComponentName(app, NotificationListener::class.java)
            if (sessionsListener == null) {
                val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }
                msm.addOnActiveSessionsChangedListener(listener, component, mainHandler)
                sessionsListener = listener
            }
            watch(msm.getActiveSessions(component))
        }.onFailure { clearPlayingSong() } // no notification access, usually
    }

    private fun watch(controllers: List<MediaController>) {
        watchedControllers.forEach { it.unregisterCallback(controllerCallback) }
        watchedControllers = controllers
        controllers.forEach { it.registerCallback(controllerCallback, mainHandler) }
        pickAndRead()
    }

    /** Prefers a player that is playing; otherwise the most recent session, which is paused. */
    private fun pickAndRead() {
        val context = appContext ?: return
        val controller = watchedControllers.firstOrNull { it.playbackState.isPlaying() }
            ?: watchedControllers.firstOrNull()
        val metadata = controller?.metadata ?: return clearPlayingSong()
        val unknown = context.getString(R.string.unknown)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: unknown
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: unknown

        when (controller.playbackState?.state) {
            PlaybackState.STATE_SKIPPING_TO_NEXT -> pendingSkip = 1
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> pendingSkip = -1
        }
        val trackKey = "$title\u0000$artist"
        if (trackKey != currentTrackKey) {
            // Players rarely report which way they skipped; going back to the song shown before
            // this one is the usual sign of "previous"
            playingSongDirection = when {
                pendingSkip != 0 -> pendingSkip
                currentTrackKey != null && trackKey == previousTrackKey -> -1
                else -> 1
            }
            pendingSkip = 0
            previousTrackKey = currentTrackKey
            currentTrackKey = trackKey
        }

        playingSongIsPlaying = controller.playbackState.isPlaying()
        playingSongTitle = title
        playingSongArtist = artist
        playingSongFilePath = allSongs?.firstOrNull {
            (it.title == title || (it.title == null && title == unknown)) &&
                (it.artist == artist || (it.artist == null && artist == unknown))
        }?.filePath?.let { "$it.nowplaying" } // shared transition uses path as key, add to avoid breaking stuff
            ?: ""

        val key = "$title\u0000$artist\u0000${controller.packageName}"
        if (key == artKey) return
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (bitmap == null) {
            // Players often send a new song's details first and its art a moment later, so keep
            // the old art briefly instead of flashing the placeholder. artKey stays unset, so the
            // art is picked up when it arrives.
            artClearJob?.cancel()
            artClearJob = viewModelScope.launch {
                delay(ART_GRACE_MS)
                if (artKey != key) playingSongAlbumArt = null
            }
            return
        }
        artClearJob?.cancel()
        artKey = key
        viewModelScope.launch(Dispatchers.IO) {
            // not same name of files because img won't update, cache dir cleared at app start
            val uri = runCatching {
                val file = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                Uri.parse(file.absolutePath)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (artKey == key) playingSongAlbumArt = uri // skip if the song changed meanwhile
            }
        }
    }

    private fun PlaybackState?.isPlaying() = when (this?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private fun clearPlayingSong() {
        artClearJob?.cancel()
        playingSongTitle = ""
        playingSongArtist = ""
        playingSongFilePath = ""
        playingSongAlbumArt = null
        playingSongIsPlaying = false
        artKey = null
        currentTrackKey = null
        previousTrackKey = null
    }

    override fun onCleared() {
        watchedControllers.forEach { it.unregisterCallback(controllerCallback) }
        watchedControllers = emptyList()
        sessionsListener?.let { sessionManager?.removeOnActiveSessionsChangedListener(it) }
        sessionsListener = null
    }
}

/** How long a song change may go without art before the placeholder shows. */
private const val ART_GRACE_MS = 800L
