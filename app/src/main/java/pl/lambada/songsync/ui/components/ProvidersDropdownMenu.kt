package pl.lambada.songsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import pl.lambada.songsync.ui.components.dropdown.AnimatedDropdownMenu
import pl.lambada.songsync.util.ProviderChoice
import pl.lambada.songsync.util.Providers

/**
 * Provider menu: Auto, Custom order, then every provider (picking one means "only this one").
 * The radio marks [selected]; [onChoose] receives the pick. Callers save it.
 */
@Composable
fun ProvidersDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selected: ProviderChoice,
    onChoose: (ProviderChoice) -> Unit,
) {
    AnimatedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        ProvidersDropdownMenuContent(
            onDismissRequest = onDismissRequest,
            selected = selected,
            onChoose = onChoose,
        )
    }
}

@Composable
fun ProvidersDropdownMenuContent(
    onDismissRequest: () -> Unit,
    selected: ProviderChoice,
    onChoose: (ProviderChoice) -> Unit,
) = Column {
    val providers = Providers.entries.filter { it.enabled }
    val scope = rememberCoroutineScope()
    fun pick(choice: ProviderChoice) {
        onChoose(choice)
        // Let the radio change register before the menu closes
        scope.launch {
            delay(200)
            onDismissRequest()
        }
    }

    @Composable
    fun Choice(choice: ProviderChoice, label: String, wordByWord: Boolean = false) = DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, modifier = Modifier.padding(start = 6.dp))
                if (wordByWord) WordByWordBadge()
                Spacer(modifier = Modifier.weight(1f))
                RadioButton(selected = selected == choice, onClick = { pick(choice) })
            }
        },
        onClick = { pick(choice) },
    )

    Text(
        text = stringResource(id = R.string.provider),
        modifier = Modifier.padding(start = 18.dp, top = 8.dp),
        fontSize = 12.sp
    )
    Choice(ProviderChoice.Auto, stringResource(R.string.provider_mode_auto))
    Choice(ProviderChoice.CustomOrder, stringResource(R.string.provider_mode_my_list))
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    providers.forEach { Choice(ProviderChoice.Only(it), it.displayName, it.hasWordByWord) }
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
