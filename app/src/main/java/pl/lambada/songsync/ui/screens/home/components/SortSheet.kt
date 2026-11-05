@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.domain.model.SortOrders
import pl.lambada.songsync.domain.model.SortValues
import pl.lambada.songsync.ui.components.ConnectedButtonRow

/**
 * Sort, as a bottom sheet: every field as a chip (all visible at once) and the order as a
 * connected pair. Changes apply as they're made; the list above the sheet shows them.
 */
@Composable
fun SortSheet(
    userSettingsController: UserSettingsController,
    onDismiss: () -> Unit,
    onSortByChange: (SortValues) -> Unit,
    onSortOrderChange: (SortOrders) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(stringResource(R.string.sort), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SortValues.entries.forEach { value ->
                    val selected = userSettingsController.sortBy == value
                    FilterChip(
                        selected = selected,
                        onClick = { onSortByChange(value) },
                        label = { Text(stringResource(value.displayName)) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SortOrderToggle(userSettingsController.sortOrder, onSortOrderChange, Modifier.fillMaxWidth())
        }
    }
}

/** Ascending / Descending as a connected pair of toggle buttons with arrows. */
@Composable
fun SortOrderToggle(order: SortOrders, onChange: (SortOrders) -> Unit, modifier: Modifier = Modifier) {
    ConnectedButtonRow(modifier) {
        SortOrders.entries.forEachIndexed { index, entry ->
            ToggleButton(
                checked = order == entry,
                onCheckedChange = { onChange(entry) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    SortOrders.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Icon(
                    if (entry == SortOrders.ASCENDING) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                Text(stringResource(entry.displayName))
            }
        }
    }
}
