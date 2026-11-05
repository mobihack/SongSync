@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A connected button group: buttons side by side with a small gap, outer corners fully round and
 * inner corners small, morphing on press. Give each button [connectedButtonShapes] for its place.
 */
@Composable
fun ConnectedButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

/** Shapes for button [index] of [count] in a [ConnectedButtonRow]. */
@Composable
fun connectedButtonShapes(index: Int, count: Int): ButtonShapes {
    if (count == 1) return ButtonDefaults.shapes()
    val shapes = when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }
    return ButtonShapes(shape = shapes.shape, pressedShape = shapes.pressedShape)
}
