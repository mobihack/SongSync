package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.ui.components.LinkItem
import pl.lambada.songsync.ui.components.ListGroup

@Composable
fun ContributorsSection(uriHandler: UriHandler) {
    val roles = Contributor.entries.associateWith { stringResource(id = it.contributionLevel.stringResource) }
    ListGroup {
        Contributor.entries.forEach { contributor ->
            row {
                LinkItem(
                    label = contributor.devName,
                    description = roles.getValue(contributor),
                    shapes = it,
                    onClick = { contributor.github?.let(uriHandler::openUri) },
                )
            }
        }
    }
}
