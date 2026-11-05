package pl.lambada.songsync.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.ui.common.animatedComposable
import pl.lambada.songsync.ui.screens.home.HomeScreen
import pl.lambada.songsync.ui.screens.home.HomeViewModel
import pl.lambada.songsync.ui.screens.init.InitScreen
import pl.lambada.songsync.ui.screens.init.InitScreenViewModel
import pl.lambada.songsync.ui.screens.lyricsFetch.LyricsFetchScreen
import pl.lambada.songsync.ui.screens.lyricsFetch.LyricsFetchViewModel
import pl.lambada.songsync.ui.screens.settings.SettingsScreen
import pl.lambada.songsync.ui.screens.settings.SettingsSection
import pl.lambada.songsync.ui.screens.settings.SettingsSectionScreen
import pl.lambada.songsync.ui.screens.settings.SettingsViewModel
import pl.lambada.songsync.ui.screens.settings.folders.MusicFoldersScreen

/**
 * Composable function for handling navigation within the app.
 *
 * @param navController The navigation controller.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Navigator(
    navController: NavHostController,
    userSettingsController: UserSettingsController,
    lyricsProviderService: LyricsProviderService
) {
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = if (userSettingsController.passedInit) ScreenHome else InitScreen,
        ) {
            animatedComposable<InitScreen> {
                InitScreen(
                    navController = navController,
                    viewModel = viewModel {
                        InitScreenViewModel(userSettingsController)
                    },
                )
            }
            animatedComposable<ScreenHome> {
                HomeScreen(
                    navController = navController,
                    viewModel = viewModel {
                        HomeViewModel(userSettingsController, lyricsProviderService)
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }

            animatedComposable<LyricsFetchScreen>() {
                val args = it.toRoute<LyricsFetchScreen>()

                LyricsFetchScreen(
                    viewModel = viewModel {
                        LyricsFetchViewModel(
                            args.source(),
                            userSettingsController,
                            lyricsProviderService
                        )
                    },
                    navController = navController,
                    animatedVisibilityScope = this,
                )
            }
            animatedComposable<ScreenMusicFolders> {
                MusicFoldersScreen(userSettingsController, navController)
            }
            animatedComposable<ScreenSettings> {
                SettingsScreen(userSettingsController, navController = navController)
            }
            animatedComposable<ScreenSettingsSection> {
                SettingsSectionScreen(
                    section = SettingsSection.valueOf(it.toRoute<ScreenSettingsSection>().section),
                    viewModel = viewModel { SettingsViewModel() },
                    userSettingsController = userSettingsController,
                    navController = navController,
                )
            }
        }
    }
}

@Serializable
object InitScreen

@Serializable
object ScreenHome

@Serializable
data class LyricsFetchScreen(
    private val songName: String? = null,
    private val artists: String? = null,
    private val coverUri: String? = null,
    private val filePath: String? = null,
    private val durationMs: Long = 0, // 0 = unknown
) {
    fun source() = if (songName != null && artists != null && filePath != null) {
        LocalSong(songName, artists, coverUri, filePath, durationMs.takeIf { it > 0 })
    } else null
}

@Serializable
data class LocalSong(
    val songName: String,
    val artists: String,
    val coverUri: String?,
    val filePath: String,
    val durationMs: Long? = null,
)

@Serializable
object ScreenSettings

/** A category page of Settings; [section] is a [SettingsSection] name. */
@Serializable
data class ScreenSettingsSection(val section: String)

@Serializable
object ScreenMusicFolders