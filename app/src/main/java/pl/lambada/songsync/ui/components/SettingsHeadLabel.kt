package pl.lambada.songsync.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHeadLabel(
    label: String,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        // Lines up with the text inside grouped rows (group margin + row padding)
        modifier = Modifier.padding(
            start = 32.dp,
            top = 24.dp,
            end = 32.dp,
            bottom = 8.dp
        ),
        color = MaterialTheme.colorScheme.primary
    )
}