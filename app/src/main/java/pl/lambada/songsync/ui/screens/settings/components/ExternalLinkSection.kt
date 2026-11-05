package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.LinkItem

@Composable
fun ExternalLinkSection(url: String, uriHandler: UriHandler, shapes: ListItemShapes) = LinkItem(
    label = stringResource(R.string.we_are_open_source),
    description = stringResource(R.string.view_on_github),
    shapes = shapes,
    onClick = { uriHandler.openUri(url) },
)
