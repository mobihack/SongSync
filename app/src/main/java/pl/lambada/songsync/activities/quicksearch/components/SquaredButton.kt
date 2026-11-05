@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.activities.quicksearch.components

import pl.lambada.songsync.util.ui.EaseOutStrong
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import pl.lambada.songsync.ui.components.ConnectedButtonRow
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SquareButtons() {
    // Connected pair: inner corners small, outer ones round (no hand-cut corners)
    ConnectedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        ButtonWithIconAndText(
            icon = Icons.Default.Home,
            text = "Home",
            modifier = Modifier.size(96.dp),
        )
        ButtonWithIconAndText(
            icon = Icons.Default.Settings,
            text = "Settings",
            modifier = Modifier.size(96.dp),
        )
    }
}

@Composable
fun ButtonWithIconAndText(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    border: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    onClick: () -> Unit = {}
) {

    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.4f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    // Squeezes to small corners while pressed, like Expressive buttons: fast on press,
    // springing back on release
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 8.dp else 16.dp, // shapes.small / shapes.large
        animationSpec = if (pressed) tween(100, easing = EaseOutStrong)
        else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "buttonCorner",
    )

    Surface(
        modifier = modifier
            .semantics { role = Role.Button }
            .alpha(animatedAlpha),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        interactionSource = interactionSource,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        color = backgroundColor
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier = Modifier
                .padding(8.dp)
                .defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight
                )
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
fun SquareButtonsPreview() {
    SquareButtons()
}