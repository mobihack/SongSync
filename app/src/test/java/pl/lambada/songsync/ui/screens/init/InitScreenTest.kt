package pl.lambada.songsync.ui.screens.init

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.ScreenHome
import java.io.File
import pl.lambada.songsync.ui.InitScreen as InitRoute

/** Setup on Android 11+, where storage comes from all-files access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InitScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var nav: NavHostController

    private fun showSetup(storageAccess: Boolean): UserSettingsController {
        val settings = UserSettingsController(PreferenceDataStoreFactory.create { File(tmp.root, "s.preferences_pb") })
        val viewModel = InitScreenViewModel(settings, hasStorageAccess = { storageAccess })
        compose.setContent {
            nav = rememberNavController()
            MaterialTheme {
                NavHost(nav, startDestination = InitRoute) {
                    composable<InitRoute> { InitScreen(nav, viewModel) }
                    composable<ScreenHome> { Text("library") }
                }
            }
        }
        return settings
    }

    @Test
    fun `without all-files access, Continue is disabled and says why`() {
        showSetup(storageAccess = false)

        compose.onNodeWithText(context.getString(R.string.setup_continue_needs_files)).assertIsNotEnabled()
    }

    @Test
    fun `with all-files access, Continue finishes setup and leaves it behind`() {
        val settings = showSetup(storageAccess = true)

        compose.onNodeWithText(context.getString(R.string._continue)).assertIsEnabled().performClick()
        compose.waitForIdle()

        settings.passedInit shouldBe true
        nav.currentBackStack.value.mapNotNull { it.destination.route?.substringAfterLast('.') } shouldBe listOf("ScreenHome")
    }
}
