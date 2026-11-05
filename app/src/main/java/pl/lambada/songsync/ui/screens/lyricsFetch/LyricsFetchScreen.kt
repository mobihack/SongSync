@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.lyricsFetch

import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import pl.lambada.songsync.ui.components.frostedTopBar
import pl.lambada.songsync.ui.components.frostedTopBarColors
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import pl.lambada.songsync.ui.components.providerChoiceLabel
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.ProvidersSheet
import pl.lambada.songsync.ui.screens.lyricsFetch.components.CloudProviderTitle
import pl.lambada.songsync.ui.screens.lyricsFetch.components.FailedDialogue
import pl.lambada.songsync.ui.screens.lyricsFetch.components.LocalSongContent
import pl.lambada.songsync.ui.screens.lyricsFetch.components.NoConnectionDialogue
import pl.lambada.songsync.ui.screens.lyricsFetch.components.NotSubmittedContent
import pl.lambada.songsync.ui.screens.lyricsFetch.components.SuccessContent
import pl.lambada.songsync.util.Providers
import pl.lambada.songsync.util.showToast

/**
 * Composable function for BrowseScreen component.
 *
 * @param viewModel the [LyricsFetchState] instance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LyricsFetchScreen(
    viewModel: LyricsFetchViewModel,
    navController: NavController,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberHazeState()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    var expandedProviders by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (viewModel.source != null) viewModel.loadSongInfo(context)
    }

    // The provider sheet, opened from the Get lyrics split button or from the provider name
    // above a search in progress or a result
    val providerMenu: @Composable () -> Unit = {
        if (expandedProviders) ProvidersSheet(
            selected = viewModel.userSettingsController.providerChoice,
            onDismissRequest = { expandedProviders = false },
            onChoose = {
                viewModel.userSettingsController.choose(it)
                if (viewModel.queryState != QueryStatus.NotSubmitted)
                    viewModel.loadSongInfo(context)
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = if (viewModel.source == null) "fab" else ""
                ),
                animatedVisibilityScope = animatedVisibilityScope
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                modifier = Modifier.frostedTopBar(hazeState, scrollBehavior),
                colors = frostedTopBarColors(),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navController.popBackStack(
                                navController.graph.startDestinationId,
                                false
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Text(text = stringResource(id = R.string.search))
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.source != null) {
                LocalSongContent(
                    viewModel.source,
                    animatedVisibilityScope = animatedVisibilityScope,
                    disableMarquee = viewModel.userSettingsController.disableMarquee
                )
            }

            Crossfade(
                viewModel.queryState,
                label = ""
            ) { queryState ->
                when (queryState) {
                    QueryStatus.NotSubmitted -> NotSubmittedContent(
                        querySong = viewModel.querySongName,
                        onQuerySongChange = { viewModel.querySongName = it },
                        queryArtist = viewModel.queryArtistName,
                        onQueryArtistChange = { viewModel.queryArtistName = it },
                        onGetLyricsRequest = { viewModel.loadSongInfo(context) },
                        providerLabel = providerChoiceLabel(viewModel.userSettingsController.providerChoice),
                        providerMenuExpanded = expandedProviders,
                        onProviderMenuRequest = { expandedProviders = !expandedProviders },
                        providerMenu = providerMenu,
                    )

                    // The provider stays here; the indicator and "Provider x of y" are centred
                    // on the screen (after the scrolling column)
                    QueryStatus.Pending -> Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        CloudProviderTitle(
                            // Follows the fallback live: shows the provider being tried right now
                            selectedProvider = viewModel.searchProgress?.provider
                                ?: viewModel.lookupProviders().first(),
                            onExpandProvidersRequest = { expandedProviders = true },
                            menu = providerMenu,
                        )
                    }

                    is QueryStatus.Success -> SuccessContent(
                        result = queryState.song,
                        onTryAgain = {
                            viewModel.lrcOffset = 0
                            viewModel.loadSongInfo(context, true)
                        },
                        onEdit = {
                            viewModel.lrcOffset = 0
                            viewModel.queryState = QueryStatus.NotSubmitted
                        },
                        directOffset = viewModel.userSettingsController.directlyModifyTimestamps,
                        offset = viewModel.lrcOffset,
                        onSetOffset = { viewModel.lrcOffset = it },
                        onSaveLyrics = {
                            viewModel.saveLyricsToFile(
                                it,
                                queryState.song,
                                viewModel.source?.filePath?.replace(".nowplaying", ""),
                                context,
                                context.getString(R.string.generated_using)
                            )
                        },
                        onEmbedLyrics = {
                            viewModel.embedLyrics(
                                it,
                                viewModel.source?.filePath?.replace(".nowplaying", ""),
                                context,
                                queryState.song
                            )
                        },
                        onCopyLyrics = {
                            clipboardManager.setText(AnnotatedString(it))

                            showToast(
                                context,
                                context.getString(R.string.lyrics_copied_to_clipboard),
                            )
                        },
                        onLanguageSelected = { songId, language ->
                            viewModel.fetchLyricsInLanguage(songId, language)
                        },
                        openUri = uriHandler::openUri,
                        lyricsFetchState = viewModel.lyricsFetchState,
                        animatedVisibilityScope = animatedVisibilityScope,
                        disableMarquee = viewModel.userSettingsController.disableMarquee,
                        allowTryingAgain = viewModel.activeProvider != Providers.MUSIXMATCH,
                        // Show the provider that actually answered (may be a fallback)
                        selectedProvider = viewModel.activeProvider,
                        onExpandProvidersRequest = { expandedProviders = true },
                        providerMenu = providerMenu,
                        songPath = viewModel.source?.filePath?.replace(".nowplaying", ""),
                    )

                    is QueryStatus.Failed -> FailedDialogue(
                        onDismissRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
                        onOkRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
                        exception = queryState.exception
                    )

                    QueryStatus.NoConnection -> NoConnectionDialogue(
                        onDismissRequest = { viewModel.queryState = QueryStatus.NotSubmitted },
                        onOkRequest = { viewModel.queryState = QueryStatus.NotSubmitted }
                    )
                }
            }
        }
        // Searching: indicator and "Provider x of y" in the middle of the screen
        AnimatedVisibility(
            visible = viewModel.queryState == QueryStatus.Pending,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val progress = viewModel.searchProgress
                    LoadingIndicator()
                    if (progress != null && progress.total > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.provider_progress, progress.index, progress.total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        }
    }
}
