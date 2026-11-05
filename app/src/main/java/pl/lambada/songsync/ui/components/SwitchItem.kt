@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.CombinedModifier
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A labelled switch. Pass [shapes] (from [ListGroup] or [listGroupItems]) to draw it as a row of
 * a rounded group; without it, it's a flat row, as used inside dialogs.
 */
@Composable
fun SwitchItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    innerPaddingValues: PaddingValues = PaddingValues(
        horizontal = 22.dp,
        vertical = 16.dp
    ),
    description: String = "",
    shapes: ListItemShapes? = null,
    onClick: () -> Unit,
) {
    if (shapes != null) {
        SegmentedListItem(
            checked = selected,
            onCheckedChange = { onClick() },
            shapes = shapes,
            colors = listGroupColors(),
            modifier = modifier,
            supportingContent = description.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            trailingContent = { CheckSwitch(selected) },
        ) {
            Text(label)
        }
        return
    }

    Row(
        modifier = CombinedModifier(
            inner = Modifier
                .clickable { onClick() }
                .padding(innerPaddingValues),
            outer = modifier
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        CheckSwitch(selected, onCheckedChange = { onClick() })
    }
}

/** Switch with a check in the thumb when on, as in the Expressive spec. */
@Composable
private fun CheckSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)? = null) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = if (checked) {
            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(SwitchDefaults.IconSize)) }
        } else null,
    )
}
