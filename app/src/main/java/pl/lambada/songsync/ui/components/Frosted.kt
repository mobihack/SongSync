@file:OptIn(ExperimentalHazeMaterialsApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Frosted glass for floating controls: blurs whatever [state] marks as its source (the song
 * list), tinted with [tint], with a hairline edge so it reads on busy covers. Android 12+ blurs;
 * older versions get the tint alone (Haze's fallback). Without a [state] it's a solid fill.
 */
@Composable
fun Modifier.frosted(
    state: HazeState?,
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainer,
    /** Thicker glass shows more of [tint]: for coloured primary actions. */
    thick: Boolean = false,
): Modifier {
    val edge = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    return this
        .clip(shape)
        .then(
            if (state != null) Modifier.hazeEffect(
                state,
                if (thick) HazeMaterials.thick(containerColor = tint) else HazeMaterials.thin(containerColor = tint),
            )
            else Modifier.background(tint)
        )
        .border(1.dp, edge, shape)
}
