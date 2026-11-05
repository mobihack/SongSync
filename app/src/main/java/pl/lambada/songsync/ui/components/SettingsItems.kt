@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ItemPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)

/**
 * One option of a single-choice group, styled like [SwitchItem]. Pass [shapes] to draw it as a
 * row of a rounded group.
 */
@Composable
fun RadioItem(
    label: String,
    selected: Boolean,
    description: String = "",
    shapes: ListItemShapes? = null,
    onClick: () -> Unit,
) {
    if (shapes != null) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            colors = listGroupColors(),
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            supportingContent = description.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        ) {
            Text(label)
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(ItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        ItemTexts(label, description, Modifier.weight(1f))
    }
}

/** A row that opens another screen. Pass [shapes] to draw it as a row of a rounded group. */
@Composable
fun NavigationItem(
    label: String,
    description: String = "",
    modifier: Modifier = Modifier,
    shapes: ListItemShapes? = null,
    onClick: () -> Unit,
) {
    if (shapes != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = listGroupColors(),
            modifier = modifier,
            supportingContent = description.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            trailingContent = { Chevron() },
        ) {
            Text(label)
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(ItemPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ItemTexts(label, description, Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Chevron()
    }
}

/** A grouped row that opens a web page. */
@Composable
fun LinkItem(
    label: String,
    description: String = "",
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = listGroupColors(),
        supportingContent = description.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(label)
    }
}

@Composable
private fun Chevron() = Icon(
    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ItemTexts(label: String, description: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label)
        if (description.isNotEmpty()) {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}
