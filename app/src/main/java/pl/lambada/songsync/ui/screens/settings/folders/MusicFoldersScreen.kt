package pl.lambada.songsync.ui.screens.settings.folders

import pl.lambada.songsync.ui.components.ListGroup
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.ui.unit.IntOffset
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

private val ItemPlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Fades rows in and out and slides them into place, like the My list editor. */
private fun LazyItemScope.animated(): Modifier =
    Modifier.animateItem(
        fadeInSpec = tween(150),
        placementSpec = ItemPlacementSpec,
        fadeOutSpec = tween(100),
    )

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SubScreenTopBar(
                title = stringResource(R.string.music_folders),
                scrollBehavior = scrollBehavior,
                onBack = { navController.popBackStack() },
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item(key = "modes") {
                val update = userSettingsController::updateFolderMode
                ListGroup(Modifier.padding(top = 8.dp)) {
                    row {
                        RadioItem(
                            label = stringResource(R.string.folder_mode_all),
                            description = stringResource(R.string.folder_mode_all_summary),
                            selected = mode == FolderMode.ALL,
                            shapes = it,
                            onClick = { update(FolderMode.ALL) },
                        )
                    }
                    row {
                        RadioItem(
                            label = stringResource(R.string.folder_mode_ignore),
                            description = stringResource(R.string.folder_mode_ignore_summary),
                            selected = mode == FolderMode.IGNORE,
                            shapes = it,
                            onClick = { update(FolderMode.IGNORE) },
                        )
                    }
                    row {
                        RadioItem(
                            label = stringResource(R.string.folder_mode_only),
                            description = stringResource(R.string.folder_mode_only_summary),
                            selected = mode == FolderMode.ONLY,
                            shapes = it,
                            onClick = { update(FolderMode.ONLY) },
                        )
                    }
                }
            }

            if (mode != FolderMode.ALL) {
                val loaded = folders
                val counts = loaded?.toMap().orEmpty()

                // Copies of the ticked folders, in the same order as the full list
                val tickedRows = ticked.sortedBy { it.lowercase() }
                if (tickedRows.isNotEmpty()) {
                    item(key = "tickedHeader") {
                        Box(animated()) {
                            SettingsHeadLabel(
                                stringResource(
                                    if (mode == FolderMode.IGNORE) R.string.ticked_folders_ignore
                                    else R.string.ticked_folders_only
                                )
                            )
                        }
                    }
                    items(tickedRows, key = { "ticked:$it" }) { folder ->
                        Box(animated()) {
                            FolderRow(
                                folder = folder,
                                songCount = counts[folder] ?: 0,
                                checked = true,
                                onCheckedChange = { toggle(folder, it) },
                            )
                        }
                    }
                }

                item(key = "allHeader") {
                    Box(animated()) {
                        SettingsHeadLabel(
                            stringResource(
                                if (mode == FolderMode.IGNORE) R.string.folders_to_ignore
                                else R.string.folders_to_show
                            )
                        )
                    }
                }
                when {
                    loaded == null -> item(key = "loading") {
                        Box(
                            animated().fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    loaded.isEmpty() -> item(key = "empty") {
                        Text(
                            text = stringResource(R.string.no_music_folders),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = animated().padding(22.dp),
                        )
                    }
                    else -> items(loaded, key = { "all:${it.first}" }) { (folder, count) ->
                        Box(animated()) {
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
