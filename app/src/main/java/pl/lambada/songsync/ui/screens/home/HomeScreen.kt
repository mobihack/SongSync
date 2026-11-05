package pl.lambada.songsync.ui.screens.home

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import pl.lambada.songsync.ui.screens.home.components.HomeOverflowMenu
import pl.lambada.songsync.ui.screens.home.components.LibraryHeader
import pl.lambada.songsync.ui.screens.home.components.HomeLibraryToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import pl.lambada.songsync.ui.screens.home.components.HomeSelectionToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.lambada.songsync.R
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.ui.LyricsFetchScreen
import pl.lambada.songsync.ui.ScreenSettings
import pl.lambada.songsync.ui.screens.home.components.BatchDownloadLyrics
import pl.lambada.songsync.ui.screens.home.components.FiltersSheet
import pl.lambada.songsync.ui.screens.home.components.HomeAppBar
import pl.lambada.songsync.ui.screens.home.components.HomeSearchBar
import pl.lambada.songsync.ui.screens.home.components.SongItem
import pl.lambada.songsync.ui.screens.home.components.SortSheet
import pl.lambada.songsync.util.ext.BackPressHandler
import pl.lambada.songsync.util.ext.lowercaseWithLocale
import pl.lambada.songsync.util.ui.SearchFABBoundsTransform
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import pl.lambada.songsync.domain.model.SortValues
import pl.lambada.songsync.ui.screens.home.components.FastScroller
import pl.lambada.songsync.ui.screens.home.components.sectionLetter
import pl.lambada.songsync.ui.ScreenMusicFolders
import pl.lambada.songsync.ui.screens.settings.folders.folderModeSummary

/**
 * Composable function representing the home screen.
 *
 * @param viewModel The [HomeViewModel] instance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = viewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val listState = rememberLazyListState()
    val listScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    var isBatchDownload by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selecting = viewModel.selectedSongs.isNotEmpty()
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val hazeState = rememberHazeState()

    // Re-filter whenever a filter setting changes, here or in Settings > Music folders
    val settings = viewModel.userSettingsController
    LaunchedEffect(settings.folderMode, settings.blacklistedFolders, settings.onlyFolders, settings.hideLyrics) {
        viewModel.filterSongs()
    }

    LaunchedEffect(viewModel.userSettingsController.sortBy to viewModel.userSettingsController.sortOrder) {
        viewModel.cachedSongs = null
        viewModel.updateAllSongs(context, viewModel.userSettingsController.sortBy, viewModel.userSettingsController.sortOrder)
    }

    Scaffold(
        topBar = {
            var cachedSize by remember { mutableIntStateOf(1) }
            if (viewModel.selectedSongs.size > 0) {
                // keep displaying "1 selected" during fade-out, don't say "0 selected"
                cachedSize = viewModel.selectedSongs.size
            }

            BackPressHandler(
                enabled = viewModel.selectedSongs.size > 0,
                onBackPressed = { viewModel.selectedSongs.clear() }
            )

            // One bar: its colour and title animate on their own, so the two states never overlap
            HomeAppBar(
                showing = viewModel.selectedSongs.size > 0,
                scrolled = listScrolled,
                hazeState = hazeState,
                onSelectedClearAction = viewModel.selectedSongs::clear,
                searchLyricsAction = {
                    // Lyrics search for any song; grows into the Search screen
                    with(sharedTransitionScope) {
                        IconButton(
                            onClick = { navController.navigate(LyricsFetchScreen()) },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier
                                .skipToLookaheadSize()
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "fab"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = SearchFABBoundsTransform,
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                                contentDescription = stringResource(R.string.search_lyrics),
                            )
                        }
                    }
                },
                cachedSize = cachedSize
            )
        },
        bottomBar = { Spacer(Modifier.navigationBarsPadding()) } // fixing broken edge to edge here
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
        // The song list is what the frosted floating controls blur
        Crossfade(viewModel.allSongs == null, modifier = Modifier.hazeSource(hazeState), label = "") { loading ->
            if (loading)
                LoadingScreen()
            else
                HomeScreenLoaded(
                    navController = navController,
                    viewModel = viewModel,
                    selected = viewModel.selectedSongs,
                    scaffoldPadding = paddingValues,
                    isBatchDownload = isBatchDownload,
                    onBatchDownloadState = { onBatchDownload -> isBatchDownload = onBatchDownload },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    listState = listState,
                )
        }
        // ⋮ menu in the bottom-start corner; the selection toolbar replaces it while selecting
        AnimatedVisibility(
            visible = viewModel.allSongs != null && !selecting,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = paddingValues.calculateBottomPadding() + 16.dp),
            // Grows from and shrinks toward its bottom-start corner, where it is anchored
            enter = fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(
                motionScheme.defaultSpatialSpec(),
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(0f, 1f),
            ),
            exit = fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(
                motionScheme.fastSpatialSpec(),
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(0f, 1f),
            ),
        ) {
            HomeOverflowMenu(
                hazeState = hazeState,
                onNavigateToSettingsSectionRequest = { navController.navigate(ScreenSettings) },
                providerChoice = viewModel.userSettingsController.providerChoice,
                onProviderChoice = viewModel.userSettingsController::choose,
                onBatchDownloadRequest = { isBatchDownload = true },
            )
        }
        // Library tools (sort, filter, search) normally; selection actions while songs are selected
        if (viewModel.allSongs != null) AnimatedContent(
            targetState = selecting,
            // Right-aligned, where a FAB would sit
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = paddingValues.calculateBottomPadding() + 16.dp),
            contentAlignment = Alignment.BottomEnd,
            transitionSpec = {
                (fadeIn(motionScheme.defaultEffectsSpec()) +
                    slideInVertically(motionScheme.defaultSpatialSpec()) { it / 2 }) togetherWith
                    (fadeOut(motionScheme.fastEffectsSpec()) +
                        slideOutVertically(motionScheme.fastSpatialSpec()) { it / 2 }) using
                    SizeTransform(clip = false) // let the toolbars slide instead of wiping
            },
            label = "homeToolbar",
        ) { selectionMode ->
            if (selectionMode) HomeSelectionToolbar(
                hazeState = hazeState,
                onSelectAll = {
                    viewModel.selectedSongs.clear()
                    viewModel.selectAllDisplayingSongs()
                },
                onInvertSelection = viewModel::invertSongSelection,
                onBatchDownload = { isBatchDownload = true },
            ) else HomeLibraryToolbar(
                hazeState = hazeState,
                onSort = { viewModel.showSort = true },
                onFilter = { viewModel.showFilters = true },
                filterActive = viewModel.userSettingsController.hideLyrics,
                onSearch = {
                    scope.launch {
                        // The search item is zero-height while hidden, so a scroll that runs
                        // alongside its expansion can stop with the next item on top, and the bar
                        // grows above the viewport. Pin item 0 as the first visible item first,
                        // then open the search so it expands downward into view.
                        listState.scrollToItem(0)
                        viewModel.showSearch = true
                        viewModel.showingSearch = true
                    }
                },
            )
        }
        }
    }
}

/**
 * Composable function representing the loading screen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingIndicator()
    }
}

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun HomeScreenLoaded(
    selected: SnapshotStateList<String>,
    navController: NavHostController,
    viewModel: HomeViewModel,
    scaffoldPadding: PaddingValues,
    isBatchDownload: Boolean,
    onBatchDownloadState: (isBatchDownload: Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    listState: LazyListState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshState = rememberPullToRefreshState()

    // Give the tapped row its shared-element modifiers first, then navigate on the next frame
    // so the transition finds a matching element in the list.
    fun openWithTransition(key: String, navigate: () -> Unit) {
        viewModel.transitionKey = key
        scope.launch {
            withFrameNanos { }
            navigate()
        }
    }

    // Outside the list, so they open however far it's scrolled
    if (viewModel.showSort) {
        SortSheet(
            userSettingsController = viewModel.userSettingsController,
            onDismiss = { viewModel.showSort = false },
            onSortOrderChange = {
                viewModel.userSettingsController.updateSortOrder(
                    it
                )
            },
            onSortByChange = { viewModel.userSettingsController.updateSortBy(it) }
        )
    }

    if (viewModel.showFilters) {
        FiltersSheet(
            hideLyrics = viewModel.userSettingsController.hideLyrics,
            folderSummary = folderModeSummary(viewModel.userSettingsController),
            onDismiss = { viewModel.showFilters = false },
            onHideLyricsChange = viewModel::onHideLyricsChange,
            onOpenMusicFolders = {
                viewModel.showFilters = false
                navController.navigate(ScreenMusicFolders)
            },
        )
    }

    if (isBatchDownload) {
        BatchDownloadLyrics(
            viewModel = viewModel,
            onDone = { onBatchDownloadState(false) })
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        state = refreshState,
        onRefresh = {
            viewModel.isRefreshing = true
            scope.launch {
                val startedAt = System.currentTimeMillis()
                viewModel.cachedSongs = null
                viewModel.updateAllSongs(context, viewModel.userSettingsController.sortBy, viewModel.userSettingsController.sortOrder)
                    .join()
                // A rescan can finish in a few ms; keep the indicator up long enough to be seen,
                // otherwise it snaps away as if the pull didn't register
                delay((MIN_REFRESH_INDICATOR_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0))
                viewModel.isRefreshing = false
            }
        },
        indicator = {
            // Material 3 Expressive pull-to-refresh: a contained, shape-morphing loading indicator
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = viewModel.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = scaffoldPadding.calculateTopPadding()),
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = scaffoldPadding
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    // Library search, opened from the search button in the bottom toolbar
                    AnimatedVisibility(
                        visible = viewModel.showingSearch,
                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                            // Heights don't overshoot: a bounce would open a gap under the field
                            expandVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)),
                    ) {
                        HomeSearchBar(
                            query = viewModel.searchQuery,
                            onQueryChange = { newQuery ->
                                viewModel.searchQuery = newQuery
                                viewModel.updateSearchResults(newQuery.lowercaseWithLocale())
                                viewModel.showingSearch = true
                            },
                            showSearch = viewModel.showSearch,
                            onShowSearchChange = { viewModel.showSearch = it },
                            showingSearch = viewModel.showingSearch,
                            onShowingSearchChange = { viewModel.showingSearch = it }
                        )
                    }

                }
            }

            item {
                if (viewModel.showNowPlaying) {
                    Text(stringResource(id = R.string.now_playing_song))
                    SongItem(
                        filePath = viewModel.playingSongFilePath,
                        selected = false,
                        quickSelect = false,
                        onSelectionChanged = {},
                        onNavigateToSongRequest = {
                            openWithTransition(viewModel.playingSongFilePath) {
                                navController.navigate(
                                    LyricsFetchScreen(
                                        songName = viewModel.playingSongTitle,
                                        artists = viewModel.playingSongArtist,
                                        coverUri = viewModel.playingSongAlbumArt.toString(),
                                        filePath = viewModel.playingSongFilePath
                                    )
                                )
                            }
                        },
                        song = Song(
                            title = viewModel.playingSongTitle,
                            artist = viewModel.playingSongArtist,
                            imgUri = viewModel.playingSongAlbumArt,
                            filePath = viewModel.playingSongFilePath
                        ),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedElementsEnabled = viewModel.transitionKey == viewModel.playingSongFilePath,
                        disableMarquee = viewModel.userSettingsController.disableMarquee,
                        showPath = viewModel.userSettingsController.showPath
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item(key = "libraryHeader") {
                Column {
                    if (viewModel.showNowPlaying) HorizontalDivider()
                    LibraryHeader(songCount = viewModel.displaySongs.size)
                }
            }

            items(
                items = viewModel.displaySongs,
                key = { song -> song.filePath ?: error("a song in the list of files did not have a file path") }
            ) { song ->
                SongItem(
                    filePath = song.filePath
                        ?: error("a song in the list of files did not have a file path"),
                    selected = song.filePath in viewModel.selectedSet,
                    quickSelect = selected.size > 0,
                    onSelectionChanged = { newValue ->
                        viewModel.selectSong(song, newValue)
                    },
                    onNavigateToSongRequest = {
                        openWithTransition(song.filePath) {
                            navController.navigate(
                                LyricsFetchScreen(
                                    songName = song.title ?: error("song.title was null"),
                                    artists = song.artist ?: "",
                                    coverUri = song.imgUri.toString(),
                                    filePath = song.filePath,
                                    durationMs = song.durationMs ?: 0
                                )
                            )
                        }
                    },
                    song = song,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedElementsEnabled = viewModel.transitionKey == song.filePath,
                    disableMarquee = viewModel.userSettingsController.disableMarquee,
                    showPath = viewModel.userSettingsController.showPath
                )
            }

            // Room for the floating toolbar, so the last song can scroll above it
            item { Spacer(modifier = Modifier.height(96.dp)) }
        }

        FastScroller(
            listState = listState,
            itemCount = viewModel.displaySongs.size,
            firstItemIndex = 3, // search header + now-playing item + library header
            contentPadding = scaffoldPadding,
            sectionLabel = when (viewModel.userSettingsController.sortBy) {
                SortValues.TITLE -> { index -> sectionLetter(viewModel.displaySongs.getOrNull(index)?.title) }
                SortValues.ARTIST -> { index -> sectionLetter(viewModel.displaySongs.getOrNull(index)?.artist) }
                else -> null
            },
        )
    }
}

/** Shortest time the pull-to-refresh indicator stays visible. */
private const val MIN_REFRESH_INDICATOR_MS = 700L
