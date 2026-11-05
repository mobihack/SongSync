@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.lambada.songsync.R
import pl.lambada.songsync.util.ProviderChoice
import pl.lambada.songsync.util.Providers

/**
 * Lyrics provider picker, a dialog like Sort: Auto and Custom order, then every provider
 * (picking one means "only this one"). The list scrolls; the current choice is selected and
 * [onChoose] receives the pick, after which the dialog closes. Callers save the choice.
 */
@Composable
fun ProvidersDialog(
    selected: ProviderChoice,
    onChoose: (ProviderChoice) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.provider)) },
        text = {
            Column {
                HorizontalDivider()
                // Taller than the screen on small phones; only the list scrolls
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    ProviderChoiceList(selected = selected, onChoose = onChoose, onDismissRequest = onDismissRequest)
                }
                HorizontalDivider()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

/** The choices of [ProvidersDialog]: radio rows, closing the dialog shortly after a pick. */
@Composable
fun ProviderChoiceList(
    selected: ProviderChoice,
    onChoose: (ProviderChoice) -> Unit,
    onDismissRequest: () -> Unit,
) = Column(Modifier.selectableGroup()) {
    val providers = Providers.entries.filter { it.enabled }
    val scope = rememberCoroutineScope()
    fun pick(choice: ProviderChoice) {
        onChoose(choice)
        // Let the radio change register before the dialog closes
        scope.launch {
            delay(200)
            onDismissRequest()
        }
    }

    @Composable
    fun Choice(choice: ProviderChoice, label: String, wordByWord: Boolean = false) = Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected == choice, role = Role.RadioButton, onClick = { pick(choice) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == choice, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
        if (wordByWord) WordByWordBadge()
    }

    Spacer(Modifier.height(4.dp))
    Choice(ProviderChoice.Auto, stringResource(R.string.provider_mode_auto))
    Choice(ProviderChoice.CustomOrder, stringResource(R.string.provider_mode_my_list))
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    providers.forEach { Choice(ProviderChoice.Only(it), it.displayName, it.hasWordByWord) }
    Spacer(Modifier.height(4.dp))
}

/** Short name of a provider choice, e.g. "Auto" or "LRCLib". */
@Composable
fun providerChoiceLabel(choice: ProviderChoice): String = when (choice) {
    ProviderChoice.Auto -> stringResource(R.string.provider_mode_auto)
    ProviderChoice.CustomOrder -> stringResource(R.string.provider_mode_my_list)
    is ProviderChoice.Only -> choice.provider.displayName
}

/** Small "W" badge for providers with word-by-word lyrics. */
@Composable
fun WordByWordBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant)
            .size(14.dp)
    ) {
        Text(
            text = "W",
            color = MaterialTheme.colorScheme.background,
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
        )
    }
}
