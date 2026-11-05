package pl.lambada.songsync.ui.screens.lyricsFetch.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.util.Providers

@Composable
fun CloudProviderTitle(
    selectedProvider: Providers,
    onExpandProvidersRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onExpandProvidersRequest)
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Cloud,
            contentDescription = null,
            Modifier.padding(end = 5.dp)
        )
        // Animates as fallback moves from one provider to the next
        AnimatedContent(
            targetState = selectedProvider,
            transitionSpec = {
                (slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(120)) { -it / 2 } + fadeOut(tween(120))) using
                    SizeTransform(clip = false)
            },
            label = "provider",
        ) { provider ->
            Text(text = provider.displayName)
        }
    }
}