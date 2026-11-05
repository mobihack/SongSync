package pl.lambada.songsync.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.lambada.songsync.util.networking.AppProxy
import pl.lambada.songsync.util.networking.ProxyMode
import pl.lambada.songsync.util.networking.ProxySettings
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyTypingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @After
    fun resetProxy() = AppProxy.update(ProxySettings(), pool = null)

    private fun settings() = UserSettingsController(
        PreferenceDataStoreFactory.create { File(tmp.root, "settings.preferences_pb") },
        saveDispatcher = dispatcher,
    )

    private val manual = ProxySettings(mode = ProxyMode.MANUAL, port = 8080)

    @Test
    fun `typing shows at once but applies once, after a pause`() = runTest(dispatcher) {
        val settings = settings()
        listOf("1", "12", "127.0.0.1").forEach {
            settings.updateProxySettings(manual.copy(host = it), debounce = true)
            advanceTimeBy(100) // keystrokes 100 ms apart
        }

        settings.proxySettings.host shouldBe "127.0.0.1" // the form already shows it
        AppProxy.settings shouldBe ProxySettings() // but no connection was dropped yet

        advanceTimeBy(UserSettingsController.PROXY_TYPING_DELAY_MS)
        runCurrent()
        AppProxy.settings shouldBe manual.copy(host = "127.0.0.1")
    }

    @Test
    fun `a toggle applies at once, along with any text still waiting`() = runTest(dispatcher) {
        val settings = settings()
        settings.updateProxySettings(manual.copy(host = "proxy"), debounce = true)
        settings.updateProxySettings(manual.copy(host = "proxy", port = 3128)) // e.g. switched type

        AppProxy.settings shouldBe manual.copy(host = "proxy", port = 3128)
        advanceTimeBy(UserSettingsController.PROXY_TYPING_DELAY_MS * 2)
        runCurrent()
        AppProxy.settings shouldBe manual.copy(host = "proxy", port = 3128) // the typed save was dropped
    }
}
