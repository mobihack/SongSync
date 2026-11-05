package pl.lambada.songsync.ui.screens.home

import android.util.Log
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
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
import pl.lambada.songsync.ui.screens.home.components.BatchCounts
import pl.lambada.songsync.ui.screens.home.components.UiState
import pl.lambada.songsync.util.saveBatchLyrics
import pl.lambada.songsync.util.ext.toLrcFile
import java.io.File
import java.io.OutputStream
import java.util.UUID

/**
 * Writes the now-playing cover with [write] to a new file in [cacheDir] and deletes [previous], so
 * covers don't pile up while songs change. A new name each time, so the image view reloads.
 *
 * @return the new file
 */
internal fun replaceNowPlayingArt(cacheDir: File, previous: File?, write: (OutputStream) -> Unit): File {
    val file = File(cacheDir, "now_playing_${UUID.randomUUID()}.jpg")
    file.outputStream().use(write)
    previous?.takeIf { it != file }?.delete()
    return file
}

/**
 * The songs the list shows: [filtered] (or [all] when nothing is filtered), narrowed to those whose
 * title or artist contains [query].
 */
internal fun songsToDisplay(all: List<Song>, filtered: List<Song>?, query: String): List<Song> {
    val songs = filtered ?: all
    if (query.isEmpty()) return songs
    return songs.filter {
        it.title?.contains(query, ignoreCase = true) == true ||
            it.artist?.contains(query, ignoreCase = true) == true
    }
}

/**
 * ViewModel class for the main functionality of the app.
 */
class HomeViewModel(
    val userSettingsController: UserSettingsController,
    private val lyricsProviderService: LyricsProviderService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    var cachedSongs: List<Song>? = null
        private set
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

    var displaySongs by mutableStateOf(allSongs ?: listOf())

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

    // Recomputed whenever the songs, the filters or the query change, so a search always matches
    // the current filters; the latest inputs win over a slower earlier search
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun updateSongsToDisplay() = coroutineScope {
        combine(
            snapshotFlow { allSongs }.filterNotNull(),
            _cachedFilteredSongs,
            snapshotFlow { searchQuery },
        ) { all, filtered, query -> Triple(all, filtered, query) }
            .mapLatest { (all, filtered, query) -> songsToDisplay(all, filtered, query) }
            .flowOn(computeDispatcher)
            .collect { newDisplaySongs ->
                displaySongs = newDisplaySongs
            }
    }

    private var loadJob: Job? = null
    private var loadedSort: Pair<SortValues, SortOrders>? = null

    /**
     * Loads the library sorted by [sortBy] and [sortOrder], unless it is already loaded that way
     * (back from the lyrics screen, or after rotation) and [force] isn't set. A new load replaces
     * one still running, so an older sort can't overwrite a newer one.
     */
    fun loadSongs(context: Context, sortBy: SortValues, sortOrder: SortOrders, force: Boolean = false): Job? {
        val sort = sortBy to sortOrder
        if (!force && sort == loadedSort && allSongs != null) return null
        loadJob?.cancel()
        return viewModelScope.launch(ioDispatcher) {
            // e.g. storage permission revoked: keep the list as it was rather than crash
            val songs = try {
                getAllSongs(context, sortBy, sortOrder)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Couldn't load the library: ${e.message}")
                return@launch
            }
            ensureActive()
            cachedSongs = songs
            loadedSort = sort
            allSongs = songs
            filterSongs()
            withContext(mainDispatcher) { updatePlayingSongInfo(context) }
        }.also { loadJob = it }
    }

    /**
     * Pull to refresh: rescans the library, keeping [isRefreshing] up for at least [minIndicatorMs]
     * so the indicator doesn't snap away. It always comes down, even if the rescan is cancelled.
     */
    fun refreshSongs(context: Context, minIndicatorMs: Long) {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                loadSongs(context, userSettingsController.sortBy, userSettingsController.sortOrder, force = true)?.join()
                delay((minIndicatorMs - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
            } finally {
                isRefreshing = false
            }
        }
    }

    /**
     * Loads all songs from the MediaStore.
     * @param context The application context.
     * @return A list of Song objects representing the songs.
     */
    private fun getAllSongs(context: Context, sortBy: SortValues, sortOrder: SortOrders): List<Song> {
        return run {
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
            songs
        }
    }

    /**
     * Applies the folder setting (all / ignore / only) and "no lyrics only" to the loaded songs.
     */
    fun filterSongs() = viewModelScope.launch(computeDispatcher) {
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

    /**
     * The batch download dialog's step and counts. Kept here rather than in the dialog so they
     * survive rotation, while the download itself runs on in [viewModelScope].
     */
    var batchUiState by mutableStateOf(UiState.Warning)
    var batchCounts by mutableStateOf(BatchCounts())
        private set

    /** Starts a batch download of [songsToBatchDownload], replacing any one still running. */
    fun batchDownloadLyrics(context: Context) {
        batchJob?.cancel()
        val appContext = context.applicationContext
        val settings = userSettingsController
        batchCounts = BatchCounts()
        batchUiState = UiState.Pending
        batchJob = viewModelScope.launch {
            downloadLyrics(
                songs = songsToBatchDownload,
                findLyrics = { song ->
                    findLyrics(SongInfo(song.searchTitle, song.artist, durationMs = song.durationMs))?.value
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
                onProgressUpdate = { success, noLyrics, failed ->
                    batchCounts = BatchCounts(success, noLyrics, failed)
                },
                onDownloadComplete = { batchUiState = UiState.Done },
                onRateLimitReached = { batchUiState = UiState.RateLimited },
            )
        }
    }

    /** Stops the running batch download, if any, and resets the dialog for next time. */
    fun closeBatchDownload() {
        batchJob?.cancel()
        batchJob = null
        batchUiState = UiState.Warning
        batchCounts = BatchCounts()
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
    private var nowPlayingArtFile: File? = null

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
            val file = runCatching {
                replaceNowPlayingArt(context.cacheDir, nowPlayingArtFile) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file != null) nowPlayingArtFile = file
                if (artKey == key) playingSongAlbumArt = file?.let { Uri.parse(it.absolutePath) } // skip if the song changed meanwhile
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
