package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.NavigationItem
import pl.lambada.songsync.ui.components.RadioItem
import pl.lambada.songsync.util.ProviderMode

/** How providers are searched: Auto, My list (with an editor link) or the selected one only. */
@Composable
fun ProviderModeSection(
    userSettingsController: UserSettingsController,
    onEditMyList: () -> Unit,
) = Column {
    val mode = userSettingsController.providerMode
    val selected = userSettingsController.selectedProvider.displayName

    RadioItem(
        label = stringResource(R.string.provider_mode_auto),
        description = stringResource(R.string.provider_mode_auto_summary, selected),
        selected = mode == ProviderMode.AUTO,
        onClick = { userSettingsController.updateProviderMode(ProviderMode.AUTO) },
    )
    RadioItem(
        label = stringResource(R.string.provider_mode_my_list),
        description = stringResource(R.string.provider_mode_my_list_summary),
        selected = mode == ProviderMode.MY_LIST,
        onClick = { userSettingsController.updateProviderMode(ProviderMode.MY_LIST) },
    )
    RadioItem(
        label = stringResource(R.string.provider_mode_selected),
        description = stringResource(R.string.provider_mode_selected_summary, selected),
        selected = mode == ProviderMode.SELECTED,
        onClick = { userSettingsController.updateProviderMode(ProviderMode.SELECTED) },
    )
    if (mode == ProviderMode.MY_LIST) {
        NavigationItem(
            label = stringResource(R.string.edit_my_list),
            description = userSettingsController.myProviders.joinToString(" → ") { it.displayName },
            onClick = onEditMyList,
        )
    }
}
