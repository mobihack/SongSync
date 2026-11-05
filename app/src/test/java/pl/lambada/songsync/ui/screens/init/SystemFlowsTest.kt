package pl.lambada.songsync.ui.screens.init

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.ui.screens.init.components.permissions.LaunchOnce
import pl.lambada.songsync.ui.screens.init.components.permissions.launchFirstAvailable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemFlowsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a system screen opens once, not again when the activity is recreated`() {
        var launches = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LaunchOnce { launches++ } }
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore() // rotation or a theme change
        compose.waitForIdle()

        launches shouldBe 1
    }

    @Test
    fun `a missing settings screen falls back to the next`() {
        val first = Intent("missing")
        val second = Intent("present")
        val launched = mutableListOf<String?>()

        launchFirstAvailable(listOf(first, second)) {
            if (it.action == "missing") throw ActivityNotFoundException()
            launched += it.action
        } shouldBe true
        launched shouldBe listOf("present")
    }

    @Test
    fun `when no settings screen exists it says so instead of crashing`() {
        launchFirstAvailable(listOf(Intent("a"), Intent("b"))) { throw ActivityNotFoundException() } shouldBe false
    }

    @Test
    fun `only the first available screen opens`() {
        val launched = mutableListOf<String?>()
        launchFirstAvailable(listOf(Intent("a"), Intent("b"))) { launched += it.action } shouldBe true
        launched shouldBe listOf("a")
    }
}
