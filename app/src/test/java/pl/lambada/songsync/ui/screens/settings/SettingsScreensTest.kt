package pl.lambada.songsync.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.compose.rememberNavController
import io.kotest.matchers.shouldBe
import org.junit.After
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
import pl.lambada.songsync.util.networking.ProxyMode
import pl.lambada.songsync.util.networking.ProxyScope
import pl.lambada.songsync.util.networking.ProxySettings
import pl.lambada.songsync.util.networking.ProxyType
import java.io.File

/** The Settings hub and the Lyrics provider page, against real settings in a temporary DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp") // tall, so every list item is composed
class SettingsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: UserSettingsController

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create { File(tmp.root, "settings.preferences_pb") }
        settings = UserSettingsController(dataStore)
    }

    @After
    fun resetProxy() = settings.updateProxySettings(ProxySettings())

    private fun showHub() = compose.setContent {
        MaterialTheme { SettingsScreen(settings, rememberNavController()) }
    }

    private fun showSearchPage() = compose.setContent {
        MaterialTheme {
            SettingsSectionScreen(SettingsSection.SEARCH, SettingsViewModel(), settings, rememberNavController())
        }
    }

    /**
     * Robolectric doesn't deliver a tap to the last row of a [pl.lambada.songsync.ui.components.ListGroup]
     * (true of the existing provider-mode group too), so these rows are clicked through semantics.
     */
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private fun showNetworkPage() = compose.setContent {
        MaterialTheme {
            SettingsSectionScreen(SettingsSection.NETWORK, SettingsViewModel(), settings, rememberNavController())
        }
    }

    @Test
    fun `hub lists every category`() {
        showHub()
        listOf("Appearance", "Lyrics provider", "Lyrics content", "Network").forEach {
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

    @Test
    fun `hub summarises the proxy`() {
        showHub()
        compose.onNodeWithText("Direct connection").assertIsDisplayed()

        settings.updateProxySettings(
            ProxySettings(mode = ProxyMode.MANUAL, type = ProxyType.SOCKS, host = "127.0.0.1", port = 9050)
        )
        compose.onNodeWithText("SOCKS5 · 127.0.0.1:9050").assertIsDisplayed()

        settings.updateProxySettings(
            settings.proxySettings.copy(scope = ProxyScope.CHOSEN, providers = setOf(Providers.APPLE, Providers.MUSIXMATCH))
        )
        compose.onNodeWithText("SOCKS5 · 127.0.0.1:9050 · 2 providers").assertIsDisplayed()
    }

    @Test
    fun `network page shows server fields only in manual mode, and credentials only for http`() {
        showNetworkPage()
        compose.onAllNodesWithText("Host").assertCountEquals(0)
        compose.onAllNodesWithText("Test connection").assertCountEquals(0)

        compose.onNodeWithText("Manual").tap()
        settings.proxySettings.mode shouldBe ProxyMode.MANUAL
        compose.onNodeWithText("Host").assertIsDisplayed()
        compose.onNodeWithText("Test connection").assertIsDisplayed()
        // SOCKS by default: OkHttp can't log in to a SOCKS proxy
        compose.onAllNodesWithText("Username (optional)").assertCountEquals(0)

        compose.onNodeWithText("HTTP").tap()
        settings.proxySettings.type shouldBe ProxyType.HTTP
        compose.onNodeWithText("Username (optional)").assertIsDisplayed()
    }

    @Test
    fun `chosen scope lists providers and saves the ticked ones`() {
        settings.updateProxySettings(ProxySettings(mode = ProxyMode.SYSTEM))
        showNetworkPage()
        compose.onAllNodesWithText(Providers.MUSIXMATCH.displayName).assertCountEquals(0)

        compose.onNodeWithText("Only chosen providers").tap()
        compose.onNodeWithText(Providers.MUSIXMATCH.displayName).performClick()

        settings.proxySettings.scope shouldBe ProxyScope.CHOSEN
        settings.proxySettings.providers shouldBe setOf(Providers.MUSIXMATCH)
        // Saved, so a fresh controller (next launch) reads it back
        UserSettingsController(dataStore).proxySettings shouldBe settings.proxySettings
    }
}
