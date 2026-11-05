package pl.lambada.songsync.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationExtTest {

    @get:Rule
    val compose = createComposeRule()

    @Serializable object Home
    @Serializable object Settings
    @Serializable object Section

    private lateinit var nav: NavHostController

    private fun showGraph() = compose.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = Home) {
            composable<Home> { Text("home") }
            composable<Settings> { Text("settings") }
            composable<Section> { Text("section") }
        }
    }

    private fun stack() = nav.currentBackStack.value.mapNotNull { it.destination.route?.substringAfterLast('.') }

    @Test
    fun `a double tap on a row opens its page once`() {
        showGraph()
        compose.runOnIdle {
            nav.navigateOnce(Settings)
            nav.navigateOnce(Settings)
        }
        compose.waitForIdle()

        stack() shouldContainExactly listOf("Home", "Settings")
    }

    @Test
    fun `extra taps on Back during the exit animation don't pop the screens below`() {
        showGraph()
        compose.runOnIdle { nav.navigateOnce(Settings) }
        compose.runOnIdle { nav.navigateOnce(Section) }
        compose.waitForIdle()

        compose.runOnIdle {
            nav.popBackStackOnce()
            nav.popBackStackOnce() // Settings is still animating in
            nav.popBackStackOnce()
        }
        compose.waitForIdle()

        stack() shouldContainExactly listOf("Home", "Settings")
    }

    @Test
    fun `Back works normally once the screen is shown`() {
        showGraph()
        compose.runOnIdle { nav.navigateOnce(Settings) }
        compose.waitForIdle()

        compose.runOnIdle { nav.popBackStackOnce() }
        compose.waitForIdle()

        stack() shouldContainExactly listOf("Home")
    }
}
