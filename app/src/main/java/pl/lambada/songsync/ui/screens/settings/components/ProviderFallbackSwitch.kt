package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.SwitchItem

@Composable
fun ProviderFallbackSwitch(selected: Boolean, onToggle: (Boolean) -> Unit) {
    SwitchItem(
        label = stringResource(R.string.provider_fallback),
        description = stringResource(R.string.provider_fallback_summary),
        selected = selected,
        onClick = { onToggle(!selected) }
    )
}
