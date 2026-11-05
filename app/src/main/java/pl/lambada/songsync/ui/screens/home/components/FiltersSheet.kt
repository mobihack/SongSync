@file:OptIn(ExperimentalMaterial3Api::class)

package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.ListGroup
import pl.lambada.songsync.ui.components.NavigationItem
import pl.lambada.songsync.ui.components.SwitchItem

/**
 * Filters, as a bottom sheet of grouped rows like Settings: the "no lyrics only" switch and a
 * shortcut to Settings › Music folders (where folder choice lives). Applies immediately.
 */
@Composable
fun FiltersSheet(
    hideLyrics: Boolean,
    folderSummary: String,
    onDismiss: () -> Unit,
    onHideLyricsChange: (Boolean) -> Unit,
    onOpenMusicFolders: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(
                stringResource(R.string.filters),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
            )
            ListGroup {
                row {
                    SwitchItem(
                        label = stringResource(R.string.no_lyrics_only),
                        selected = hideLyrics,
                        shapes = it,
                        onClick = { onHideLyricsChange(!hideLyrics) },
                    )
                }
                row {
                    NavigationItem(
                        label = stringResource(R.string.music_folders),
                        description = folderSummary,
                        shapes = it,
                        onClick = onOpenMusicFolders,
                    )
                }
            }
        }
    }
}
