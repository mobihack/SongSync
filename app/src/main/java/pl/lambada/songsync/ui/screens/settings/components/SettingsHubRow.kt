@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Icon container shape for every settings category, so the hub reads as one set. */
private val HubIconShape = MaterialShapes.Cookie9Sided

/** A settings category: shaped tonal icon, title and a summary of its current values. */
@Composable
fun SettingsHubRow(
    icon: ImageVector,
    title: String,
    summary: String,
    shapes: ListItemShapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
    /** Secondary categories (About) use the tertiary role so they read apart from the rest. */
    tertiary: Boolean = false,
    onClick: () -> Unit,
) {
    val container: Color
    val content: Color
    if (tertiary) {
        container = MaterialTheme.colorScheme.tertiaryContainer
        content = MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        container = MaterialTheme.colorScheme.primaryContainer
        content = MaterialTheme.colorScheme.onPrimaryContainer
    }
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .background(container, HubIconShape.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            }
        },
        supportingContent = {
            Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
