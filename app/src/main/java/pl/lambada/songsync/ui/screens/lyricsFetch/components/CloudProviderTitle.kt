package pl.lambada.songsync.ui.screens.lyricsFetch.components

import pl.lambada.songsync.util.ui.tickerTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedContent
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
    /** The provider menu, anchored to this title. */
    menu: @Composable () -> Unit = {},
) = Box {
    menu()
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
            transitionSpec = { tickerTransform() },
            label = "provider",
        ) { provider ->
            Text(text = provider.displayName)
        }
    }
}