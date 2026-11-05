package pl.lambada.songsync.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeSearchThing(
    showingSearch: Boolean,
    searchBar: @Composable () -> Unit,
    filterBar: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = showingSearch,
        transitionSpec = {
            // Small travel (a quarter of the bar) so the swap reads as a toggle, not a page move.
            val direction = if (targetState) 1 else -1
            (slideInVertically { height -> direction * height / 4 } + fadeIn(tween(150)))
                .togetherWith(
                    slideOutVertically { height -> -direction * height / 4 } + fadeOut(tween(100))
                )
        },
        label = "",
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
    ) { showing ->
        if (showing) searchBar() else filterBar()
    }
}

