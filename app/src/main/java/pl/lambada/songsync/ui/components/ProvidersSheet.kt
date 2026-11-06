@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.components

import dev.chrisbanes.haze.HazeState
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectableGroup
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
 * Lyrics provider picker, a bottom sheet like Sort and Filters. It opens half-height and drags
 * up: Auto and Custom order in one group, every provider (picking one means "only this one")
 * in a second. Picking closes the sheet shortly after; callers save the choice.
 */
@Composable
fun ProvidersSheet(
    selected: ProviderChoice,
    onChoose: (ProviderChoice) -> Unit,
    onDismissRequest: () -> Unit,
    /** What the sheet's frosting blurs: the screen it opens over. */
    hazeState: HazeState? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    // Animate the sheet away instead of cutting it
    val dismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
    }
    FrostedBottomSheet(onDismissRequest = onDismissRequest, hazeState = hazeState, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp).navigationBarsPadding()) {
            Text(
                stringResource(R.string.provider),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            ProviderChoiceList(selected = selected, onChoose = onChoose, onDismissRequest = dismiss)
        }
    }
}

/** The choices of [ProvidersSheet] as grouped rows; closes shortly after a pick. */
@Composable
fun ProviderChoiceList(
    selected: ProviderChoice,
    onChoose: (ProviderChoice) -> Unit,
    onDismissRequest: () -> Unit,
) = Column(Modifier.fillMaxWidth().selectableGroup()) {
    val scope = rememberCoroutineScope()
    fun pick(choice: ProviderChoice) {
        onChoose(choice)
        // Let the radio change register before the sheet closes
        scope.launch {
            delay(200)
            onDismissRequest()
        }
    }

    @Composable
    fun Group(choices: List<ProviderChoice>) = ListGroup {
        choices.forEach { choice ->
            row { shapes -> ProviderChoiceRow(choice, selected == choice, shapes, onClick = { pick(choice) }) }
        }
    }

    Group(listOf(ProviderChoice.Auto, ProviderChoice.CustomOrder))
    SettingsHeadLabel(stringResource(R.string.provider_only_one))
    Group(Providers.entries.filter { it.enabled }.map { ProviderChoice.Only(it) })
}

/**
 * One provider choice as a grouped row: radio, name, and the word-by-word badge where it
 * applies. Shared by [ProvidersSheet] and Settings › Lyrics provider.
 */
@Composable
fun ProviderChoiceRow(
    choice: ProviderChoice,
    selected: Boolean,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = SegmentedListItem(
    selected = selected,
    onClick = onClick,
    shapes = shapes,
    colors = listGroupColors(),
    modifier = modifier,
    leadingContent = { RadioButton(selected = selected, onClick = null) },
    trailingContent = if (choice is ProviderChoice.Only && choice.provider.hasWordByWord) {
        { WordByWordBadge() }
    } else null,
) { Text(providerChoiceLabel(choice)) }

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
