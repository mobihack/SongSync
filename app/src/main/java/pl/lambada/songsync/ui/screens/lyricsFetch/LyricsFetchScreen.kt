@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.lyricsFetch

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.ProvidersSheet
import pl.lambada.songsync.ui.components.frostedTopBar
import pl.lambada.songsync.ui.components.frostedTopBarColors
import pl.lambada.songsync.ui.components.providerChoiceLabel
import pl.lambada.songsync.ui.components.rememberSystemAnimationsEnabled
import pl.lambada.songsync.ui.screens.lyricsFetch.components.FailedDialogue
import pl.lambada.songsync.ui.screens.lyricsFetch.components.LanguageSelector
import pl.lambada.songsync.ui.screens.lyricsFetch.components.MatchRow
import pl.lambada.songsync.ui.screens.lyricsFetch.components.MatchRowState
import pl.lambada.songsync.ui.screens.lyricsFetch.components.NoConnectionDialogue
import pl.lambada.songsync.ui.screens.lyricsFetch.components.NotSubmittedContent
import pl.lambada.songsync.ui.screens.lyricsFetch.components.StageHeader
import pl.lambada.songsync.ui.screens.lyricsFetch.components.StageToolbar
import pl.lambada.songsync.ui.screens.lyricsFetch.components.TimingPanel
import pl.lambada.songsync.util.LyricLine
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.activeLineIndex
import pl.lambada.songsync.util.applyOffsetToLyrics
import pl.lambada.songsync.util.parseLyricLines
import pl.lambada.songsync.util.showToast
import pl.lambada.songsync.util.ui.EaseOutStrong

/** Room under the list for the floating toolbar. */
private val ToolbarClearance = 104.dp
/** Where the line being sung sits while it follows playback: this far down the list. */
private const val FollowAnchor = 0.36f

/**
 * Lyrics for one song. A local song starts as a large cover while the search runs, then settles
 * into a compact header with the matched song under it; the lyrics fill the rest. A floating
 * toolbar plays the file (the sung line lights up, so the offset can be tuned by ear), opens the
 * timing panel, and embeds or saves.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LyricsFetchScreen(
    viewModel: LyricsFetchViewModel,
    navController: NavController,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val source = viewModel.source
    val settings = viewModel.userSettingsController

    var expandedProviders by remember { mutableStateOf(false) }
    var timingOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadInitialSongInfo(context) }
    LaunchedEffect(viewModel.queryState) {
        if (viewModel.queryState != QueryStatus.Pending) viewModel.headerCollapsed = true
    }

    val songPath = source?.filePath?.removeSuffix(".nowplaying")?.takeIf { it.isNotEmpty() }
    val player = rememberLyricsPreviewPlayer(songPath)
    val playing = player?.isPlaying == true

    val query = viewModel.queryState
    val lyrics = (viewModel.lyricsFetchState as? LyricsFetchState.Success)?.lyrics
        ?.takeIf { query is QueryStatus.Success }
    val lines = remember(lyrics) { lyrics?.let(::parseLyricLines).orEmpty() }
    val offset = viewModel.lrcOffset
    // Copied as shown: with the offset applied when it's written into the timestamps
    val shownLyrics = lyrics?.let { if (offset != 0 && settings.directlyModifyTimestamps) applyOffsetToLyrics(it, offset) else it }

    // A new result or a new search stops the preview and closes the timing panel
    LaunchedEffect(lyrics) {
        if (lyrics == null) {
            player?.pause()
            timingOpen = false
        }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, playing) {
        while (player != null && playing) {
            withFrameMillis { }
            positionMs = player.positionMs
        }
    }
    // Reads playback, position and offset as state, so it follows all three without recomposing
    val activeLine by remember(lines, player) {
        derivedStateOf {
            if (player?.isPlaying == true) activeLineIndex(lines, positionMs, viewModel.lrcOffset) else -1
        }
    }

    val providerMenu: @Composable () -> Unit = {
        if (expandedProviders) ProvidersSheet(
            selected = settings.providerChoice,
            onDismissRequest = { expandedProviders = false },
            hazeState = hazeState,
            onChoose = {
                settings.choose(it)
                if (viewModel.queryState != QueryStatus.NotSubmitted) viewModel.loadSongInfo(context)
            },
        )
    }

    BackHandler(enabled = timingOpen) { timingOpen = false }

    Scaffold(
        modifier = Modifier
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key = if (source == null) "fab" else ""),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.frostedTopBar(hazeState, scrollBehavior),
                colors = frostedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack(navController.graph.startDestinationId, false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    // The song's title, once the header has scrolled away
                    val scrolledPast by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
                    AnimatedVisibility(
                        visible = source != null && scrolledPast,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        Text(source?.songName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (shownLyrics != null) IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(shownLyrics))
                        showToast(context, context.getString(R.string.lyrics_copied_to_clipboard))
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_lyrics_to_clipboard))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + ToolbarClearance,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                if (source != null) item(key = "header") {
                    StageHeader(
                        song = source,
                        collapsed = viewModel.headerCollapsed,
                        animatedVisibilityScope = animatedVisibilityScope,
                        disableMarquee = settings.disableMarquee,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                when (query) {
                    QueryStatus.NotSubmitted -> item(key = "form") {
                        NotSubmittedContent(
                            querySong = viewModel.querySongName,
                            onQuerySongChange = { viewModel.querySongName = it },
                            queryArtist = viewModel.queryArtistName,
                            onQueryArtistChange = { viewModel.queryArtistName = it },
                            onGetLyricsRequest = { viewModel.loadSongInfo(context) },
                            providerLabel = providerChoiceLabel(settings.providerChoice),
                            providerMenuExpanded = expandedProviders,
                            onProviderMenuRequest = { expandedProviders = !expandedProviders },
                            providerMenu = providerMenu,
                        )
                    }

                    QueryStatus.Pending -> if (viewModel.headerCollapsed) {
                        item(key = "match") {
                            MatchRow(
                                state = MatchRowState.Searching(
                                    provider = viewModel.searchProgress?.provider ?: viewModel.lookupProviders().first(),
                                    progress = viewModel.searchProgress,
                                ),
                                localDurationMs = source?.durationMs,
                                localArtist = source?.artists,
                                onProviderClick = { expandedProviders = true },
                                onNextResult = null,
                                onEdit = {},
                                modifier = Modifier.padding(top = 14.dp).animateItem(),
                            )
                        }
                    } else item(key = "searching") {
                        // The first search, under the large cover
                        SearchingIndicator(
                            provider = viewModel.searchProgress?.provider ?: viewModel.lookupProviders().first(),
                            index = viewModel.searchProgress?.index,
                            total = viewModel.searchProgress?.total,
                            modifier = Modifier.animateItem(),
                        )
                    }

                    is QueryStatus.Success -> {
                        item(key = "match") {
                            MatchRow(
                                state = MatchRowState.Found(
                                    song = query.song,
                                    provider = viewModel.activeProvider,
                                    synced = lyrics?.let { lines.isNotEmpty() },
                                ),
                                localDurationMs = source?.durationMs,
                                localArtist = source?.artists,
                                onProviderClick = { expandedProviders = true },
                                onNextResult = if (viewModel.activeProvider != Providers.MUSIXMATCH) {
                                    {
                                        viewModel.lrcOffset = 0
                                        viewModel.loadSongInfo(context, true)
                                    }
                                } else null,
                                onEdit = {
                                    viewModel.lrcOffset = 0
                                    viewModel.queryState = QueryStatus.NotSubmitted
                                },
                                modifier = Modifier.padding(top = 14.dp).animateItem(),
                            )
                        }
                        if (query.song.availableLanguages.isNotEmpty()) item(key = "languages") {
                            LanguageSelector(
                                availableLanguages = query.song.availableLanguages,
                                currentLanguage = query.song.currentLanguage,
                                originalLanguage = query.song.originalLanguage,
                                onLanguageSelected = { viewModel.fetchLyricsInLanguage(query.song.musixmatchID, it) },
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        lyricsItems(
                            state = viewModel.lyricsFetchState,
                            lyrics = lyrics,
                            lines = lines,
                            offsetMs = offset,
                            activeLine = { activeLine },
                            playing = playing,
                            onLineClick = player?.let { p ->
                                { line: LyricLine -> scope.launch { p.play(fromMs = line.timeMs + viewModel.lrcOffset) } }
                            },
                        )
                        if (songPath != null) item(key = "path") { FilePath(songPath) }
                    }

                    is QueryStatus.Failed, QueryStatus.NoConnection -> Unit
                }
            }

            // Items before the first line: header, match row, languages
            val firstLineIndex = (if (source != null) 1 else 0) + 1 +
                (if (query.songOrNull()?.availableLanguages?.isNotEmpty() == true) 1 else 0)
            FollowPlayback(listState, firstLineIndex, lines.size, activeLine = { activeLine }, playing = playing)

            // Floating toolbar, with the timing panel opening above it
            if (lyrics != null) Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                AnimatedVisibility(
                    visible = timingOpen,
                    // Grows from the timing button, below-left of the panel
                    enter = fadeIn(tween(150)) + scaleIn(tween(200, easing = EaseOutStrong), initialScale = 0.94f, transformOrigin = TransformOrigin(0.25f, 1f)),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.97f, transformOrigin = TransformOrigin(0.25f, 1f)),
                ) {
                    TimingPanel(
                        offsetMs = offset,
                        onSetOffset = { viewModel.lrcOffset = it },
                        hazeState = hazeState,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                StageToolbar(
                    hazeState = hazeState,
                    playing = if (player != null && lines.isNotEmpty()) playing else null,
                    onPlayToggle = {
                        if (playing) player?.pause()
                        else scope.launch {
                            // From the first line when starting over, so there's no long intro to sit through
                            val restart = player!!.positionMs == 0L
                            player.play(fromMs = if (restart) (lines.first().timeMs + offset - 1500).coerceAtLeast(0) else null)
                        }
                    },
                    offsetMs = offset,
                    timingOpen = timingOpen,
                    onTimingToggle = { timingOpen = !timingOpen },
                    onEmbed = if (songPath != null) {
                        { viewModel.embedLyrics(lyrics, songPath, context, query.songOrNull()!!) }
                    } else null,
                    onSave = {
                        viewModel.saveLyricsToFile(
                            lyrics, query.songOrNull()!!, songPath, context, context.getString(R.string.generated_using),
                        )
                    },
                )
            }
        }
    }

    if (query !is QueryStatus.NotSubmitted) providerMenu()

    when (query) {
        is QueryStatus.Failed -> FailedDialogue(
            onDismissRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
            onOkRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
            exception = query.exception,
        )
        QueryStatus.NoConnection -> NoConnectionDialogue(
            onDismissRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
            onOkRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
        )
        else -> Unit
    }
}

private fun QueryStatus.songOrNull() = (this as? QueryStatus.Success)?.song

private fun androidx.compose.foundation.lazy.LazyListScope.lyricsItems(
    state: LyricsFetchState,
    lyrics: String?,
    lines: List<LyricLine>,
    offsetMs: Int,
    activeLine: () -> Int,
    playing: Boolean,
    onLineClick: ((LyricLine) -> Unit)?,
) {
    when {
        state is LyricsFetchState.Pending -> item(key = "lyricsLoading") {
            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
        state is LyricsFetchState.Failed -> item(key = "noLyrics") {
            Text(
                stringResource(R.string.this_track_has_no_lyrics),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
        }
        lyrics == null -> Unit
        // Plain lyrics: nothing to time, so just the text
        lines.isEmpty() -> item(key = "plainLyrics") {
            SelectionContainer {
                Text(
                    lyrics,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        else -> itemsIndexed(lines, key = { index, _ -> "line$index" }) { index, line ->
            LyricLineRow(
                line = line,
                offsetMs = offsetMs,
                active = { activeLine() == index },
                dimmed = playing,
                onClick = onLineClick?.let { { it(line) } },
                modifier = Modifier.padding(top = if (index == 0) 14.dp else 0.dp),
            )
        }
    }
}

/**
 * One timed line: its start (with the offset) and its text. While the song plays, the line being
 * sung is lit and the rest dim; tapping a line plays from it.
 */
@Composable
private fun LyricLineRow(
    line: LyricLine,
    offsetMs: Int,
    active: () -> Boolean,
    dimmed: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val lit = active()
    val alpha by animateFloatAsState(
        targetValue = if (!dimmed || lit) 1f else 0.5f,
        animationSpec = tween(200),
        label = "lyricLine",
    )
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 7.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        Text(
            formatTimestamp(line.timeMs + offsetMs),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(60.dp).padding(top = 6.dp),
        )
        Text(
            line.text.ifEmpty { "♪" },
            fontSize = 20.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Keeps the sung line in view while the song plays, unless the list is being scrolled by hand. */
@Composable
private fun FollowPlayback(listState: LazyListState, firstLineIndex: Int, lineCount: Int, activeLine: () -> Int, playing: Boolean) {
    val animate = rememberSystemAnimationsEnabled()
    val currentActiveLine by rememberUpdatedState(activeLine)
    val target by remember { derivedStateOf { currentActiveLine() } }
    LaunchedEffect(target, playing) {
        if (!playing || target < 0 || target >= lineCount || listState.isScrollInProgress) return@LaunchedEffect
        val key = "line$target"
        val anchor = (listState.layoutInfo.viewportSize.height * FollowAnchor).toInt()
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        if (item != null) {
            val delta = (item.offset - anchor).toFloat()
            if (animate) listState.animateScrollBy(delta, tween(450, easing = EaseOutStrong)) else listState.scrollBy(delta)
        } else {
            // Off screen (scrolled away by hand): jump there, then settle at the anchor
            listState.scrollToItem(firstLineIndex + target)
            listState.scrollBy(-anchor.toFloat())
        }
    }
}

@Composable
private fun SearchingIndicator(provider: Providers, index: Int?, total: Int?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(top = 32.dp),
    ) {
        LoadingIndicator()
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (index != null && total != null && total > 1) {
                provider.displayName + " · " + stringResource(R.string.provider_progress, index, total)
            } else provider.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilePath(path: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp, end = 4.dp),
    ) {
        Icon(
            Icons.Outlined.AudioFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        SelectionContainer {
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "01:02.34": minutes, seconds and hundredths. */
private fun formatTimestamp(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val minutes = (t / 60_000).toString().padStart(2, '0')
    val seconds = (t / 1000 % 60).toString().padStart(2, '0')
    val hundredths = (t / 10 % 100).toString().padStart(2, '0')
    return "$minutes:$seconds.$hundredths"
}
