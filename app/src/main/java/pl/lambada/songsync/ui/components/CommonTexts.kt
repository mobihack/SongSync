package pl.lambada.songsync.ui.components

import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Single-line text that scrolls when it overflows (if [animate] is true), otherwise ellipsizes.
 *
 * Uses [basicMarquee], which animates at draw time instead of relaying out every frame.
 * basicMarquee ignores the system animator scale, so "Remove animations" is checked
 * explicitly and falls back to an ellipsized label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedText(
    animate: Boolean,
    text: String,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val marquee = animate && rememberSystemAnimationsEnabled()

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = if (marquee) {
            modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1000,
                repeatDelayMillis = 1500,
                spacing = MarqueeSpacing.fractionOfContainer(2f / 3f),
            )
        } else modifier,
        color = color,
        maxLines = 1,
        overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
