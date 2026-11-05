package pl.lambada.songsync.ui.screens.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.LinkItem
import pl.lambada.songsync.ui.components.ListGroup

@Composable
fun CreditsSection(uriHandler: UriHandler) {
    val credits = listOf(
        stringResource(R.string.spotify_api) to "https://developer.spotify.com/documentation/web-api",
        stringResource(R.string.spotifylyrics_api) to "https://github.com/akashrchandran/spotify-lyrics-api",
        stringResource(R.string.syncedlyrics_py) to "https://github.com/0x7d4/syncedlyrics",
        stringResource(R.string.statusbar_lyrics_ext) to "https://github.com/cjybyjk/StatusBarLyricExt"
    )
    ListGroup(Modifier.padding(bottom = 16.dp)) {
        credits.forEach { (name, url) ->
            row { LinkItem(label = name, shapes = it, onClick = { uriHandler.openUri(url) }) }
        }
    }
}
