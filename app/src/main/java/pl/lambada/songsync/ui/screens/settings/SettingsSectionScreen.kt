package pl.lambada.songsync.ui.screens.settings

import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import pl.lambada.songsync.ui.components.ListGroup
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.UpdateState
import pl.lambada.songsync.services.PowerampPlugin
import pl.lambada.songsync.ui.ScreenMusicFolders
import pl.lambada.songsync.ui.components.NavigationItem
import pl.lambada.songsync.ui.components.SettingsHeadLabel
import pl.lambada.songsync.ui.components.SwitchItem
import pl.lambada.songsync.ui.screens.settings.components.AppInfoSection
import pl.lambada.songsync.ui.screens.settings.components.ContributorsSection
import pl.lambada.songsync.ui.screens.settings.components.CreditsSection
import pl.lambada.songsync.ui.screens.settings.components.ExternalLinkSection
import pl.lambada.songsync.ui.screens.settings.components.MarqueeSwitch
import pl.lambada.songsync.ui.screens.settings.components.MultiPersonSwitch
import pl.lambada.songsync.ui.screens.settings.components.OffsetModeSwitch
import pl.lambada.songsync.ui.screens.settings.components.ProviderModeSection
import pl.lambada.songsync.ui.screens.settings.components.PureBlackThemeSwitch
import pl.lambada.songsync.ui.screens.settings.components.RomanizationSwitch
import pl.lambada.songsync.ui.screens.settings.components.SdCardPathSetting
import pl.lambada.songsync.ui.screens.settings.components.ShowPathSwitch
import pl.lambada.songsync.ui.screens.settings.components.SubScreenTopBar
import pl.lambada.songsync.ui.screens.settings.components.SupportSection
import pl.lambada.songsync.ui.screens.settings.components.SyncedLyricsSwitch
import pl.lambada.songsync.ui.screens.settings.components.TranslationSection
import pl.lambada.songsync.ui.screens.settings.components.TranslationSwitch
import pl.lambada.songsync.ui.screens.settings.components.UpdateAvailableDialog
import pl.lambada.songsync.ui.screens.settings.folders.folderModeSummary
import pl.lambada.songsync.ui.screens.settings.network.network
import pl.lambada.songsync.ui.screens.settings.providers.MyProvidersEditorState
import pl.lambada.songsync.ui.screens.settings.providers.myProvidersEditor
import pl.lambada.songsync.ui.screens.settings.providers.rememberMyProvidersEditor
import pl.lambada.songsync.ui.screens.settings.providers.selectedProviderPicker
import pl.lambada.songsync.util.ProviderMode
import pl.lambada.songsync.util.ext.getVersion

/** The categories on the Settings home screen. */
enum class SettingsSection { APPEARANCE, LIBRARY, SEARCH, CONTENT, NETWORK, POWERAMP, ABOUT }

/** One category's settings on its own page. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    viewModel: SettingsViewModel,
    userSettingsController: UserSettingsController,
    navController: NavController,
) {
    val title = when (section) {
        SettingsSection.APPEARANCE -> stringResource(R.string.settings_appearance)
        SettingsSection.LIBRARY -> stringResource(R.string.library)
        SettingsSection.SEARCH -> stringResource(R.string.settings_lyrics_search)
        SettingsSection.CONTENT -> stringResource(R.string.settings_lyrics_content)
        SettingsSection.NETWORK -> stringResource(R.string.settings_network)
        SettingsSection.POWERAMP -> stringResource(R.string.poweramp)
        SettingsSection.ABOUT -> stringResource(R.string.about_songsync)
    }

    val listState = rememberLazyListState()
    // Lyrics search: the My list editor follows the one "modes" item
    val editor = if (section == SettingsSection.SEARCH)
        rememberMyProvidersEditor(userSettingsController, listState, itemsBefore = 1) else null

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberHazeState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SubScreenTopBar(title = title, scrollBehavior = scrollBehavior, hazeState = hazeState, onBack = { navController.popBackStack() })
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().hazeSource(hazeState),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            when (section) {
                SettingsSection.APPEARANCE -> appearance(userSettingsController)
                SettingsSection.LIBRARY -> library(userSettingsController, navController)
                SettingsSection.SEARCH -> search(userSettingsController, editor!!)
                SettingsSection.CONTENT -> content(userSettingsController)
                SettingsSection.NETWORK -> network(userSettingsController)
                SettingsSection.POWERAMP -> poweramp(userSettingsController)
                SettingsSection.ABOUT -> about(viewModel)
            }
        }
    }

    if (section == SettingsSection.ABOUT) {
        val uriHandler = LocalUriHandler.current
        val updateState = viewModel.updateState
        if (updateState is UpdateState.UpdateAvailable) UpdateAvailableDialog(
            onDismiss = viewModel::dismissUpdate,
            onDownloadRequest = { uriHandler.openUri(updateState.release.htmlURL) },
            latestVersion = updateState.release.tagName,
            currentVersion = LocalContext.current.getVersion(),
            changelog = updateState.release.changelog ?: "",
        )
    }
}

private fun LazyListScope.appearance(settings: UserSettingsController) {
    item {
        val dark = isSystemInDarkTheme()
        ListGroup {
            if (dark) row {
                PureBlackThemeSwitch(selected = settings.pureBlack, onToggle = { settings.updatePureBlack(it) }, shapes = it)
            }
            row { MarqueeSwitch(selected = settings.disableMarquee, onToggle = { settings.updateDisableMarquee(it) }, shapes = it) }
            row { ShowPathSwitch(selected = settings.showPath, onToggle = { settings.updateShowPath(it) }, shapes = it) }
        }
    }
}

/** Only reached before Android 11, where the SD card location is needed too. */
private fun LazyListScope.library(settings: UserSettingsController, navController: NavController) {
    item {
        val summary = folderModeSummary(settings)
        ListGroup {
            row {
                NavigationItem(
                    label = stringResource(R.string.music_folders),
                    description = summary,
                    shapes = it,
                    onClick = { navController.navigate(ScreenMusicFolders) },
                )
            }
        }
    }
    item { SettingsHeadLabel(label = stringResource(R.string.sd_card)) }
    item {
        SdCardPathSetting(
            sdPath = settings.sdCardPath,
            onClearPath = { settings.updateSdCardPath("") },
            onUpdatePath = { settings.updateSdCardPath(it) },
        )
    }
}

private fun LazyListScope.search(settings: UserSettingsController, editor: MyProvidersEditorState) {
    item(key = "modes") { ProviderModeSection(userSettingsController = settings) }
    // Custom order edits its list, Selected provider picks its one; Auto needs neither
    when (settings.providerMode) {
        ProviderMode.MY_LIST -> myProvidersEditor(editor)
        ProviderMode.SELECTED -> selectedProviderPicker(settings)
        ProviderMode.AUTO -> Unit
    }
}

private fun LazyListScope.content(settings: UserSettingsController) {
    item {
        ListGroup {
            row { TranslationSwitch(selected = settings.includeTranslation, onToggle = { settings.updateIncludeTranslation(it) }, shapes = it) }
            row { RomanizationSwitch(selected = settings.includeRomanization, onToggle = { settings.updateIncludeRomanization(it) }, shapes = it) }
            row { MultiPersonSwitch(selected = settings.multiPersonWordByWord, onToggle = { settings.updateMultiPersonWordByWord(it) }, shapes = it) }
            row {
                SyncedLyricsSwitch(
                    selected = settings.unsyncedFallbackMusixmatch,
                    onToggle = { settings.updateUnsyncedFallbackMusixmatch(it) },
                    shapes = it,
                )
            }
            row {
                OffsetModeSwitch(
                    selected = settings.directlyModifyTimestamps,
                    onToggle = { settings.updateDirectlyModifyTimestamps(it) },
                    shapes = it,
                )
            }
        }
    }
}

private fun LazyListScope.poweramp(settings: UserSettingsController) {
    item {
        val context = LocalContext.current
        var pluginOn by remember { mutableStateOf(PowerampPlugin.isEnabled(context)) }
        ListGroup {
            row {
                SwitchItem(
                    label = stringResource(R.string.poweramp_plugin),
                    description = stringResource(R.string.poweramp_plugin_description),
                    selected = pluginOn,
                    shapes = it,
                    onClick = {
                        PowerampPlugin.setEnabled(context, !pluginOn)
                        pluginOn = !pluginOn
                    },
                )
            }
            // Saving only happens from lyrics the plugin supplied
            if (pluginOn) row {
                SwitchItem(
                    label = stringResource(R.string.poweramp_rescan_after_save),
                    description = stringResource(R.string.poweramp_rescan_after_save_description),
                    selected = settings.powerampRescanAfterSave,
                    shapes = it,
                    onClick = { settings.updatePowerampRescanAfterSave(!settings.powerampRescanAfterSave) },
                )
            }
        }
    }
}

private fun LazyListScope.about(viewModel: SettingsViewModel) {
    item {
        val context = LocalContext.current
        AppInfoSection(version = context.getVersion(), onCheckForUpdates = { viewModel.checkForUpdates(context) })
    }
    item { SettingsHeadLabel(label = stringResource(R.string.get_involved)) }
    item {
        val uriHandler = LocalUriHandler.current
        ListGroup {
            row { ExternalLinkSection(url = "https://github.com/Lambada10/SongSync", uriHandler = uriHandler, shapes = it) }
            row { SupportSection(uriHandler = uriHandler, shapes = it) }
            row { TranslationSection(uriHandler = uriHandler, shapes = it) }
        }
    }
    item { SettingsHeadLabel(stringResource(R.string.contributors)) }
    item { ContributorsSection(uriHandler = LocalUriHandler.current) }
    item { SettingsHeadLabel(label = stringResource(R.string.thanks_to)) }
    item { CreditsSection(uriHandler = LocalUriHandler.current) }
}

