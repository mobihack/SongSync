package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.SwitchItem


@Composable
fun MarqueeSwitch(selected: Boolean, onToggle: (Boolean) -> Unit, shapes: ListItemShapes? = null) {
    SwitchItem(
        label = stringResource(R.string.disable_marquee),
        description = stringResource(R.string.disable_marquee_summary),
        selected = selected,
        shapes = shapes,
        onClick = { onToggle(!selected) }
    )
}