package pl.lambada.songsync.ui.screens.lyricsFetch.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.LocalSong

/** The cover's size while searching, as the screen's centrepiece. */
val StageHeroCoverSize = 176.dp
private val HeroTextGap = 20.dp

/**
 * The cover's shape: corners in proportion to its side, which the home covers growing into it
 * match as they arrive.
 */
val StageCoverShape = RoundedCornerShape(percent = 20)

/**
 * The local song this screen is for, as its centrepiece: a large cover with the title, artist and
 * length centred under it. It stays like this once the search is done; the lyrics follow below.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.StageHeader(
    song: LocalSong,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val artist = song.artists.ifBlank { stringResource(R.string.unknown) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(StageHeroCoverSize)
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "cover${song.filePath}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    // The home-side cover stays drawn throughout and turns into this shape;
                    // this one shows only near the end, and leaves fast on back
                    enter = fadeIn(tween(durationMillis = 150, delayMillis = 200)),
                    exit = fadeOut(tween(durationMillis = 100)),
                    clipInOverlayDuringTransition = OverlayClip(StageCoverShape),
                )
                .clip(StageCoverShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            AsyncImage(
                model = song.coverUri,
                imageLoader = context.imageLoader,
                contentDescription = stringResource(R.string.album_cover),
                placeholder = painterResource(R.drawable.ic_song),
                error = painterResource(R.drawable.ic_song),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            song.songName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = HeroTextGap),
        )
        Text(
            song.durationMs?.let { "$artist · ${formatDuration(it)}" } ?: artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "3:41" for a length in milliseconds. */
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
