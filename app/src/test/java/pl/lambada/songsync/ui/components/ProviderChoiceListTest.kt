package pl.lambada.songsync.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.util.ProviderChoice
import pl.lambada.songsync.util.Providers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp")
class ProviderChoiceListTest {

    @get:Rule
    val compose = createComposeRule()

    private var chosen: ProviderChoice? = null
    private var dismissed = false

    /**
     * Robolectric's injected touches reach SegmentedListItem rows only intermittently (some rows,
     * some runs); on a device they work. Tap through the row's click action, which runs the same
     * onClick, so these tests check the wiring rather than the touch pipeline.
     */
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    private fun show(selected: ProviderChoice = ProviderChoice.Auto) = compose.setContent {
        MaterialTheme {
            ProviderChoiceList(
                onDismissRequest = { dismissed = true },
                selected = selected,
                onChoose = { chosen = it },
            )
        }
    }

    @Test
    fun `lists both modes and every enabled provider, and never Spotify`() {
        show()
        compose.onNodeWithText("Auto").assertIsDisplayed()
        compose.onNodeWithText("Custom order").assertIsDisplayed()
        Providers.entries.filter { it.enabled }.forEach {
            compose.onNodeWithText(it.displayName).assertIsDisplayed()
        }
        compose.onAllNodesWithText("Spotify").assertCountEquals(0)
    }

    @Test
    fun `only the current choice is selected`() {
        show(selected = ProviderChoice.Only(Providers.DEEZER))
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }

    @Test
    fun `tapping a provider chooses it, then closes the sheet`() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("LRCLib").tap()

        chosen shouldBe ProviderChoice.Only(Providers.LRCLIB)
        dismissed shouldBe false // the radio gets a moment to show the change

        compose.mainClock.advanceTimeBy(300)
        dismissed shouldBe true
    }

    @Test
    fun `tapping a mode chooses the mode`() {
        show()
        compose.onNodeWithText("Custom order").tap()
        chosen shouldBe ProviderChoice.CustomOrder
    }

}
