package pl.lambada.songsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * A near-solid surface for floating controls: [tint] over a soft blur of whatever [state] marks
 * as its source (the song list). Android 12+ blurs;
 * older versions get the tint alone (Haze's fallback). Without a [state] it's a solid fill.
 */
@Composable
fun Modifier.frosted(
    state: HazeState?,
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier {
    return this
        .clip(shape)
        .then(
            // A firm tint over a soft blur: the list shows through as colour, not as glass
            if (state != null) Modifier.hazeEffect(state, frostedStyle(tint.copy(alpha = FrostedTintAlpha)))
            else Modifier.background(tint)
        )
}

/** How much of a frosted surface is its tint; the rest is the blurred content behind it. */
const val FrostedTintAlpha = 0.78f

/** The frosted look as a Haze style: [tint], alpha included, over a soft blur. */
@Composable
fun frostedStyle(tint: Color) = HazeStyle(
    backgroundColor = MaterialTheme.colorScheme.surface,
    tint = HazeTint(tint),
    blurRadius = 24.dp,
    noiseFactor = 0f,
)
