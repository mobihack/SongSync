package pl.lambada.songsync.ui.screens.settings.providers

import androidx.compose.foundation.layout.height
import pl.lambada.songsync.ui.components.listGroupRow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.SettingsHeadLabel
import pl.lambada.songsync.ui.components.WordByWordBadge
import pl.lambada.songsync.util.Providers

private val PlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Same motion as the My list editor, so switching between the two cross-fades. */
private fun Modifier.pickerItem(scope: LazyItemScope) = with(scope) {
    this@pickerItem.animateItem(fadeInSpec = tween(150), placementSpec = PlacementSpec, fadeOutSpec = tween(100))
}

/**
 * Chooses the provider for Selected provider mode.
 * Same providers and order as the provider menus.
 */
fun LazyListScope.selectedProviderPicker(settings: UserSettingsController) {
    item(key = "providerDivider") {
        Spacer(Modifier.pickerItem(this).height(8.dp))
    }
    item(key = "providerHeader") {
        Box(Modifier.pickerItem(this)) { SettingsHeadLabel(stringResource(R.string.settings_selected_provider)) }
    }
    val providers = Providers.entries.filter { it.enabled }
    items(providers, key = { "pick:${it.name}" }) { provider ->
        val selected = settings.selectedProvider == provider
        Row(
            Modifier
                .pickerItem(this)
                .listGroupRow(index = providers.indexOf(provider), count = providers.size)
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { settings.updateSelectedProviders(provider) },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(16.dp))
            Text(provider.displayName)
            if (provider.hasWordByWord) WordByWordBadge()
        }
    }
}
