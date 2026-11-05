package pl.lambada.songsync.ui.screens.settings.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.ui.components.RadioItem
import pl.lambada.songsync.ui.components.SettingsHeadLabel
import pl.lambada.songsync.ui.screens.settings.components.SubScreenTopBar
import pl.lambada.songsync.util.FolderMode
import pl.lambada.songsync.util.MusicFolders

private const val InternalStorage = "/storage/emulated/0/"

/**
 * Chooses which folders the song list shows: all music, all except ignored folders, or only the
 * chosen folders. A ticked folder also covers its subfolders.
 */
@Composable
fun MusicFoldersScreen(
    userSettingsController: UserSettingsController,
    navController: NavController,
) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    LaunchedEffect(Unit) {
        folders = withContext(Dispatchers.IO) { MusicFolders.load(context) }
    }

    val mode = userSettingsController.folderMode
    val ticked = when (mode) {
        FolderMode.IGNORE -> userSettingsController.blacklistedFolders
        FolderMode.ONLY -> userSettingsController.onlyFolders
        FolderMode.ALL -> emptyList()
    }

    fun toggle(folder: String, on: Boolean) {
        val updated = if (on) ticked + folder else ticked - folder
        when (mode) {
            FolderMode.IGNORE -> userSettingsController.updateBlacklistedFolders(updated)
            FolderMode.ONLY -> userSettingsController.updateOnlyFolders(updated)
            FolderMode.ALL -> Unit
        }
    }

    Scaffold(
        topBar = {
            SubScreenTopBar(
                title = stringResource(R.string.music_folders),
                onBack = { navController.popBackStack() },
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                RadioItem(
                    label = stringResource(R.string.folder_mode_all),
                    description = stringResource(R.string.folder_mode_all_summary),
                    selected = mode == FolderMode.ALL,
                    onClick = { userSettingsController.updateFolderMode(FolderMode.ALL) },
                )
                RadioItem(
                    label = stringResource(R.string.folder_mode_ignore),
                    description = stringResource(R.string.folder_mode_ignore_summary),
                    selected = mode == FolderMode.IGNORE,
                    onClick = { userSettingsController.updateFolderMode(FolderMode.IGNORE) },
                )
                RadioItem(
                    label = stringResource(R.string.folder_mode_only),
                    description = stringResource(R.string.folder_mode_only_summary),
                    selected = mode == FolderMode.ONLY,
                    onClick = { userSettingsController.updateFolderMode(FolderMode.ONLY) },
                )
            }

            if (mode != FolderMode.ALL) {
                item {
                    SettingsHeadLabel(
                        stringResource(
                            if (mode == FolderMode.IGNORE) R.string.folders_to_ignore
                            else R.string.folders_to_show
                        )
                    )
                }
                val loaded = folders
                when {
                    loaded == null -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    loaded.isEmpty() -> item {
                        Text(
                            text = stringResource(R.string.no_music_folders),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(22.dp),
                        )
                    }
                    else -> items(loaded, key = { it.first }) { (folder, count) ->
                        FolderRow(
                            folder = folder,
                            songCount = count,
                            checked = folder in ticked,
                            onCheckedChange = { toggle(folder, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: String,
    songCount: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val parent = folder.substringBeforeLast('/', "").removePrefix(InternalStorage.trimEnd('/'))
        .removePrefix("/")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = folder.substringAfterLast('/'),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    parent.takeIf { it.isNotEmpty() },
                    pluralStringResource(R.plurals.folder_song_count, songCount, songCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** One-line description of the current folder setting, e.g. "Ignoring 2 folders". */
@Composable
fun folderModeSummary(userSettingsController: UserSettingsController): String =
    when (userSettingsController.folderMode) {
        FolderMode.ALL -> stringResource(R.string.folder_mode_all)
        FolderMode.IGNORE -> userSettingsController.blacklistedFolders.size.let {
            pluralStringResource(R.plurals.folders_summary_ignore, it, it)
        }
        FolderMode.ONLY -> userSettingsController.onlyFolders.size.let {
            pluralStringResource(R.plurals.folders_summary_only, it, it)
        }
    }
