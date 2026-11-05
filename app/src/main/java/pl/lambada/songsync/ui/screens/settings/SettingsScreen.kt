package pl.lambada.songsync.ui.screens.settings

import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.layout.padding
import pl.lambada.songsync.ui.components.ListGroup
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.services.PowerampPlugin
import pl.lambada.songsync.ui.ScreenMusicFolders
import pl.lambada.songsync.ui.ScreenSettingsSection
import pl.lambada.songsync.ui.screens.settings.components.SettingsHubRow
import pl.lambada.songsync.ui.screens.settings.components.SettingsScreenTopBar
import pl.lambada.songsync.ui.screens.settings.folders.folderModeSummary
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.ProviderMode
import pl.lambada.songsync.util.ext.getVersion

/**
 * Settings home: one row per category, each summarising its current values. A row opens the
 * category's page ([SettingsSectionScreen]); Library goes straight to Music folders.
 */
@OptIn(ExperimentalMaterial3Api::class)
/** Space between groups of settings rows. */
private val GroupSpacing = 16.dp

@Composable
fun SettingsScreen(
    userSettingsController: UserSettingsController,
    navController: NavController,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = rememberHazeState()
    fun open(section: SettingsSection) = navController.navigate(ScreenSettingsSection(section.name))

    // The plugin switch lives on another page; re-read it when coming back
    var pluginOn by remember { mutableStateOf(PowerampPlugin.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        pluginOn = PowerampPlugin.isEnabled(context)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SettingsScreenTopBar(navController = navController, scrollBehavior = scrollBehavior, hazeState = hazeState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().hazeSource(hazeState),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "core") {
                ListGroup(Modifier.padding(bottom = GroupSpacing)) {
                    row {
                        SettingsHubRow(
                            Icons.Outlined.Palette,
                            stringResource(R.string.settings_appearance),
                            appearanceSummary(userSettingsController),
                            shapes = it,
                        ) { open(SettingsSection.APPEARANCE) }
                    }
                    row {
                        SettingsHubRow(
                            Icons.Outlined.LibraryMusic,
                            stringResource(R.string.library),
                            folderModeSummary(userSettingsController),
                            shapes = it,
                        ) {
                            // Before Android 11 the SD card setting shares the page; otherwise skip it
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) open(SettingsSection.LIBRARY)
                            else navController.navigate(ScreenMusicFolders)
                        }
                    }
                    row {
                        SettingsHubRow(
                            Icons.Outlined.Route,
                            stringResource(R.string.settings_lyrics_search),
                            searchSummary(userSettingsController),
                            shapes = it,
                        ) { open(SettingsSection.SEARCH) }
                    }
                    row {
                        SettingsHubRow(
                            Icons.Outlined.Lyrics,
                            stringResource(R.string.settings_lyrics_content),
                            contentSummary(userSettingsController),
                            shapes = it,
                        ) { open(SettingsSection.CONTENT) }
                    }
                }
            }
            item(key = "integrations") {
                ListGroup(Modifier.padding(bottom = GroupSpacing)) {
                    row {
                        SettingsHubRow(
                            Icons.Outlined.Speaker,
                            stringResource(R.string.poweramp),
                            stringResource(
                                when {
                                    !pluginOn -> R.string.poweramp_plugin_off
                                    userSettingsController.powerampRescanAfterSave -> R.string.poweramp_plugin_on_rescan
                                    else -> R.string.poweramp_plugin_on
                                }
                            ),
                            shapes = it,
                        ) { open(SettingsSection.POWERAMP) }
                    }
                }
            }
            item(key = "about") {
                ListGroup {
                    row {
                        SettingsHubRow(
                            Icons.Outlined.Info,
                            stringResource(R.string.about_songsync),
                            stringResource(R.string.app_version, context.getVersion()),
                            shapes = it,
                            tertiary = true,
                        ) { open(SettingsSection.ABOUT) }
                    }
                }
            }
        }
    }
}

@Composable
private fun appearanceSummary(settings: UserSettingsController): String = listOfNotNull(
    stringResource(R.string.settings_appearance_pure_black).takeIf { settings.pureBlack && isSystemInDarkTheme() },
    stringResource(R.string.settings_appearance_paths).takeIf { settings.showPath },
    stringResource(R.string.settings_appearance_no_marquee).takeIf { settings.disableMarquee },
).joinToString(" · ").ifEmpty { stringResource(R.string.settings_appearance_default) }

@Composable
private fun searchSummary(settings: UserSettingsController): String {
    val selected = settings.selectedProvider.displayName
    return when (settings.providerMode) {
        ProviderMode.AUTO -> stringResource(R.string.settings_lyrics_search_auto, ProviderChain.defaultOrder.size)
        ProviderMode.MY_LIST -> settings.myProviders.size.let {
            pluralStringResource(R.plurals.settings_lyrics_search_custom, it, it)
        }
        ProviderMode.SELECTED -> stringResource(R.string.settings_lyrics_search_selected, selected)
    }
}

@Composable
private fun contentSummary(settings: UserSettingsController): String = listOfNotNull(
    stringResource(R.string.settings_lyrics_content_translation).takeIf { settings.includeTranslation },
    stringResource(R.string.settings_lyrics_content_romanization).takeIf { settings.includeRomanization },
    stringResource(R.string.settings_lyrics_content_multi_person).takeIf { settings.multiPersonWordByWord },
    stringResource(R.string.settings_lyrics_content_unsynced).takeIf { settings.unsyncedFallbackMusixmatch },
).joinToString(", ").ifEmpty { stringResource(R.string.settings_lyrics_content_none) }
