package pl.lambada.songsync

import pl.lambada.songsync.ui.screens.home.clearStaleCovers
import pl.lambada.songsync.util.usesAllFilesAccess
import android.Manifest
import coil.Coil
import coil.ImageLoader
import pl.lambada.songsync.util.networking.Ktor
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsProviderService
import pl.lambada.songsync.ui.Navigator
import pl.lambada.songsync.ui.components.dialogs.NoInternetDialog
import pl.lambada.songsync.ui.theme.SongSyncTheme
import pl.lambada.songsync.util.Providers
import java.io.File

/**
 * The main activity of the SongSync app.
 */
class MainActivity : ComponentActivity() {
    private val lyricsProviderService = LyricsProviderService()

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // fixes weird system bars background upon app loading
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            insets
        }

        val userSettingsController = UserSettingsController.shared(this)
        // Cover art from providers follows the proxy setting too
        Coil.setImageLoader { ImageLoader.Builder(applicationContext).okHttpClient { Ktor.baseOkHttp }.build() }
        checkOrCreateDownloadSubFolder()
        // A fresh start, not a rotation or theme change: other caches in cacheDir stay
        if (savedInstanceState == null) clearStaleCovers(cacheDir)

        setContent {
            val navController = rememberNavController()
            var networkError by rememberSaveable { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                if (networkError == null && Providers.SPOTIFY.enabled) lyricsProviderService
                    .refreshSpotifyToken()
                    .onFailure { networkError = true }
            }

            SongSyncTheme(pureBlack = userSettingsController.pureBlack) {
                if (networkError == true) NoInternetDialog(
                    onConfirm = ::finishAndRemoveTask,
                    onIgnore = { networkError = false }
                )

                // check in case user revoked permissions later
                if (userSettingsController.passedInit)
                    CheckForPermissions(
                        userSettingsController = userSettingsController
                    )

                Surface(modifier = Modifier.fillMaxSize()) {
                    Navigator(
                        navController = navController,
                        userSettingsController = userSettingsController,
                        lyricsProviderService = lyricsProviderService
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun MainActivity.CheckForPermissions(
    userSettingsController: UserSettingsController
) {
    if (usesAllFilesAccess()) {
        if (!Environment.isExternalStorageManager()) {
            userSettingsController.updatePassedInit(false)
        }
    } else {
        val permissions = rememberMultiplePermissionsState(
            permissions = listOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
        if (!permissions.allPermissionsGranted) {
            userSettingsController.updatePassedInit(false)
        }
    }
}

private fun checkOrCreateDownloadSubFolder() {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    )

    val songSyncDir = File(downloadsDir, "SongSync+")

    if (!songSyncDir.exists()) songSyncDir.mkdir()
}
