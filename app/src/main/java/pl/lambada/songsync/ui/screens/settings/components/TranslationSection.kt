package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.LinkItem

@Composable
fun TranslationSection(uriHandler: UriHandler, shapes: ListItemShapes) = LinkItem(
    label = stringResource(id = R.string.help_us_translate),
    description = stringResource(id = R.string.translation_website),
    shapes = shapes,
    onClick = { uriHandler.openUri("https://hosted.weblate.org/engage/songsync/") },
)
