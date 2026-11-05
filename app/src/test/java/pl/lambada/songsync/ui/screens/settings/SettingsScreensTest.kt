package pl.lambada.songsync.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.compose.rememberNavController
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.util.ProviderChain
import pl.lambada.songsync.util.ProviderMode
import pl.lambada.songsync.util.Providers
import java.io.File

/** The Settings hub and the Lyrics provider page, against real settings in a temporary DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp") // tall, so every list item is composed
class SettingsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var settings: UserSettingsController

    @Before
    fun setUp() {
        settings = UserSettingsController(
            PreferenceDataStoreFactory.create { File(tmp.root, "settings.preferences_pb") }
        )
    }

    private fun showHub() = compose.setContent {
        MaterialTheme { SettingsScreen(settings, rememberNavController()) }
    }

    private fun showSearchPage() = compose.setContent {
        MaterialTheme {
            SettingsSectionScreen(SettingsSection.SEARCH, SettingsViewModel(), settings, rememberNavController())
        }
    }

    @Test
    fun `hub lists every category`() {
        showHub()
        listOf("Appearance", "Lyrics provider", "Lyrics content").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `hub summarises the provider mode and follows changes to it`() {
        showHub()
        compose.onNodeWithText("Auto · all ${ProviderChain.defaultOrder.size} providers").assertIsDisplayed()

        settings.updateProviderMode(ProviderMode.SELECTED)
        settings.updateSelectedProviders(Providers.APPLE)
        compose.onNodeWithText("Only Apple Music").assertIsDisplayed()
    }

    @Test
    fun `selected mode lists the enabled providers, without Spotify, and picks one`() {
        settings.updateProviderMode(ProviderMode.SELECTED)
        showSearchPage()

        Providers.entries.filter { it.enabled }.forEach {
            compose.onNodeWithText(it.displayName).assertIsDisplayed()
        }
        compose.onAllNodesWithText("Spotify").assertCountEquals(0)

        compose.onNodeWithText("Deezer").performClick()
        settings.selectedProvider shouldBe Providers.DEEZER
    }

    @Test
    fun `auto mode shows no provider list`() {
        showSearchPage() // a fresh install starts in Auto
        compose.onAllNodesWithText("Deezer").assertCountEquals(0)
    }

    @Test
    fun `choosing custom order saves the mode and shows its list`() {
        showSearchPage()
        compose.onNodeWithText("Custom order").performClick()

        settings.providerMode shouldBe ProviderMode.MY_LIST
        compose.onNodeWithText(ProviderChain.defaultOrder.first().displayName).assertIsDisplayed()
    }
}
