@file:OptIn(ExperimentalMaterial3Api::class)

package pl.lambada.songsync.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Names an icon-only button: long-press it (or hover with a mouse) and [text] shows above it.
 * Use the button's content description, so what it says matches what a screen reader says.
 */
@Composable
fun WithTooltip(
    text: String,
    modifier: Modifier = Modifier,
    /** Below the button instead, for buttons along the top of the screen. */
    below: Boolean = false,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (below) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
