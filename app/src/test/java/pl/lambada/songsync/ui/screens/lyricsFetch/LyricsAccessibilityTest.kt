package pl.lambada.songsync.ui.screens.lyricsFetch

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.screens.home.components.HomeSearchBar
import pl.lambada.songsync.ui.screens.lyricsFetch.components.LyricsSuccessContent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the offset buttons are named for screen readers`() {
        var offset = 0
        compose.setContent {
            MaterialTheme {
                LyricsSuccessContent(
                    lyrics = "[00:01.00]Hi", offset = offset, onSetOffset = { offset = it },
                    onSaveLyrics = {}, onEmbedLyrics = {}, onCopyLyrics = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.offset_increase)).assertHasClickAction()
        compose.onNodeWithContentDescription(context.getString(R.string.offset_decrease)).assertHasClickAction()
    }

    @Test
    fun `the search bar's clear icon is a button that clears the query`() {
        var query = "adele"
        compose.setContent {
            MaterialTheme {
                HomeSearchBar(
                    query = query, onQueryChange = { query = it },
                    showSearch = true, onShowSearchChange = {},
                    showingSearch = true, onShowingSearchChange = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.clear)).assertHasClickAction().performClick()
        query shouldBe ""
    }

    @Test
    fun `the original language label is translatable`() {
        context.getString(R.string.language_original, "Spanish") shouldBe "Spanish (Original)"
    }
}
