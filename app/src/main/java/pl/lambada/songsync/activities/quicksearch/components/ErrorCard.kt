@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.activities.quicksearch.components

import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorCard(
    stacktrace: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Error icon in an Expressive shape: one of the few hero-shape spots in the app
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.error, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Error,
                    contentDescription = stringResource(R.string.error),
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp)
                    .weight(1f),
            ) {
                Text(
                    modifier = Modifier.alpha(0.65f),
                    text = stringResource(R.string.unknown_error_occurred).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stacktrace,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Preview
@Composable
private fun ErrorCardPreview() {
    ErrorCard(
        modifier = Modifier.heightIn(max = 300.dp),
        stacktrace = "java.lang.IllegalArgumentException: Unsupported AnimationVector type\n" +
                "                                                                                                    \tat pl.lambada.songsync.util.ui.DurationBasedCustomAnimationsKt.minus(DurationBasedCustomAnimations.kt:112)\n" +
                "                                                                                                    \tat pl.lambada.songsync.util.ui.DurationBasedCustomAnimationsKt.access\$minus(DurationBasedCustomAnimations.kt:1)\n" +
                "                                                                                                    \tat pl.lambada.songsync.util.ui.VectorizedPixelAnimationSpec.getValueFromNanos(DurationBasedCustomAnimations.kt:57)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.TargetBasedAnimation.getValueFromNanos(Animation.kt:265)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.Transition\$TransitionAnimationState.seekTo\$animation_core_release(Transition.kt:1416)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.Transition.seekAnimations\$animation_core_release(Transition.kt:1256)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.Transition.seekAnimations\$animation_core_release(Transition.kt:1260)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.Transition.seekAnimations\$animation_core_release(Transition.kt:1260)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.SeekableTransitionState.seekToFraction(Transition.kt:744)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.SeekableTransitionState.access\$seekToFraction(Transition.kt:224)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.SeekableTransitionState\$animateOneFrameLambda\$1.invoke(Transition.kt:333)\n" +
                "                                                                                                    \tat androidx.compose.animation.core.SeekableTransitionState\$animateOneFrameLambda\$1.invoke(Transition.kt:311)\n" +
                "                                                                                                    \tat androidx.compose.runtime.BroadcastFrameClock\$FrameAwaiter.resume(BroadcastFrameClock.kt:42)"
    )
}