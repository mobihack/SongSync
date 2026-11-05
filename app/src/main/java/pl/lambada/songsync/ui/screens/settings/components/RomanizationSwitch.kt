package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.SwitchItem

@Composable
fun RomanizationSwitch(selected: Boolean, onToggle: (Boolean) -> Unit, shapes: ListItemShapes? = null) {
    SwitchItem(
        label = stringResource(id = R.string.include_romanization),
        description = stringResource(id = R.string.include_romanization_summary),
        selected = selected,
        shapes = shapes,
        onClick = { onToggle(!selected) }
    )
}