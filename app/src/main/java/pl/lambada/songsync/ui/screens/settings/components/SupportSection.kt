package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.LinkItem

/** Bug reports and suggestions go to the project's GitHub issues. */
@Composable
fun SupportSection(uriHandler: UriHandler, shapes: ListItemShapes) = LinkItem(
    label = stringResource(R.string.support_issues),
    description = stringResource(R.string.support_issues_summary),
    shapes = shapes,
    onClick = { uriHandler.openUri("https://github.com/mobihack/SongSync/issues") },
)
