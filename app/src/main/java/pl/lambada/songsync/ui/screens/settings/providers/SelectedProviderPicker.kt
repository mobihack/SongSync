package pl.lambada.songsync.ui.screens.settings.providers

import pl.lambada.songsync.util.ProviderChoice
import pl.lambada.songsync.ui.components.listGroupItems
import pl.lambada.songsync.ui.components.ProviderChoiceRow
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
    item(key = "providerHeader") {
        Box(Modifier.pickerItem(this)) { SettingsHeadLabel(stringResource(R.string.provider_only_one)) }
    }
    // Same rows as the Lyrics provider sheet
    val choices = Providers.entries.filter { it.enabled }.map { ProviderChoice.Only(it) }
    listGroupItems(choices, key = { "pick:${it.provider.name}" }) { choice, shapes, padding ->
        ProviderChoiceRow(
            choice = choice,
            selected = settings.selectedProvider == choice.provider,
            shapes = shapes,
            modifier = Modifier.pickerItem(this).padding(padding),
            onClick = { settings.updateSelectedProviders(choice.provider) },
        )
    }
}
