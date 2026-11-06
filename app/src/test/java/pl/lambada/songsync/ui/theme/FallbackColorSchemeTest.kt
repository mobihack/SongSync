package pl.lambada.songsync.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class FallbackColorSchemeTest {

    @Test
    fun `pure black applies in dark theme`() {
        fallbackColorScheme(darkTheme = true, pureBlack = true).background shouldBe Color.Black
    }

    @Test
    fun `pure black is ignored while the system is light`() {
        val scheme = fallbackColorScheme(darkTheme = false, pureBlack = true)

        scheme.background shouldNotBe Color.Black
        scheme shouldBe fallbackColorScheme(darkTheme = false, pureBlack = false)
    }

    @Test
    fun `dark theme without pure black isn't black`() {
        fallbackColorScheme(darkTheme = true, pureBlack = false).background shouldNotBe Color.Black
    }

    @Test
    fun `pure black layers are white over black, getting lighter as they rise`() {
        val scheme = fallbackColorScheme(darkTheme = true, pureBlack = true)
        val layers = listOf(
            scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer,
            scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
        )

        scheme.surface shouldBe Color.Black
        layers.forEach { it.red shouldBe it.green; it.green shouldBe it.blue } // grey: no tint
        layers.zipWithNext().forEach { (lower, higher) -> (higher.red > lower.red) shouldBe true }
    }

    @Test
    fun `pure black's accent is white, with black on it`() {
        val scheme = fallbackColorScheme(darkTheme = true, pureBlack = true)

        scheme.primary shouldBe Color.White
        scheme.onPrimary shouldBe Color.Black
    }
}
