package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.ListGroup
import pl.lambada.songsync.ui.components.RadioItem
import pl.lambada.songsync.util.ProviderMode

/** How providers are searched: Auto, My list or the selected one only. */
@Composable
fun ProviderModeSection(userSettingsController: UserSettingsController) {
    val mode = userSettingsController.providerMode
    val selected = userSettingsController.selectedProvider.displayName
    val update = userSettingsController::updateProviderMode

    ListGroup {
        row {
            RadioItem(
                label = stringResource(R.string.provider_mode_auto),
                description = stringResource(R.string.provider_mode_auto_summary),
                selected = mode == ProviderMode.AUTO,
                shapes = it,
                onClick = { update(ProviderMode.AUTO) },
            )
        }
        row {
            RadioItem(
                label = stringResource(R.string.provider_mode_my_list),
                description = stringResource(R.string.provider_mode_my_list_summary),
                selected = mode == ProviderMode.MY_LIST,
                shapes = it,
                onClick = { update(ProviderMode.MY_LIST) },
            )
        }
        row {
            RadioItem(
                label = stringResource(R.string.provider_mode_selected),
                description = stringResource(R.string.provider_mode_selected_summary, selected),
                selected = mode == ProviderMode.SELECTED,
                shapes = it,
                onClick = { update(ProviderMode.SELECTED) },
            )
        }
    }
}
