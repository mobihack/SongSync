package pl.lambada.songsync.util.ext

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackPressHandler(
    backPressedDispatcher: OnBackPressedDispatcher? =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
    enabled: Boolean = true,
    onBackPressed: () -> Unit
) {
    val realEnabled = enabled && !WindowInsets.isImeVisible
    val currentOnBackPressed by rememberUpdatedState(newValue = onBackPressed)

    val backCallback = remember(key1 = backPressedDispatcher, key2 = realEnabled) {
        object : OnBackPressedCallback(realEnabled) {
            override fun handleOnBackPressed() {
                currentOnBackPressed()
            }
        }
    }

    DisposableEffect(key1 = backPressedDispatcher, key2 = realEnabled) {
        backPressedDispatcher?.addCallback(backCallback)

        onDispose {
            backCallback.remove()
        }
    }
}

fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    maxDelayMillis: Long = 1000,
    minDelayMillis: Long = 5,
    delayDecayFactor: Float = .20f,
    /** [repeat] is false for the click on press, true for the ones that follow while held. */
    onClick: (repeat: Boolean) -> Unit
): Modifier = this.then(
    composed {
        val currentClickListener by rememberUpdatedState(onClick)

        pointerInput(interactionSource, enabled) {
            // Gestures run inside this block, not a coroutine launched from it: Compose starts the
            // block on the first touch, so a gesture loop launched elsewhere would start too late
            // to see that touch, and each button's first press was lost.
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPress = PressInteraction.Press(down.position)
                    // Undispatched, so the first click happens on the touch itself and a quick tap
                    // can't release before it runs
                    val heldButtonJob = launch(start = CoroutineStart.UNDISPATCHED) {
                        interactionSource.emit(downPress)
                        var currentDelayMillis = maxDelayMillis
                        var repeat = false
                        while (enabled) {
                            currentClickListener(repeat)
                            repeat = true
                            delay(currentDelayMillis)
                            val nextMillis = currentDelayMillis - (currentDelayMillis * delayDecayFactor)
                            currentDelayMillis = nextMillis.toLong().coerceAtLeast(minDelayMillis)
                        }
                    }
                    // Lifting the finger ends the repeat
                    val up = waitForUpOrCancellation()
                    heldButtonJob.cancel()
                    val releaseOrCancel = when (up) {
                        null -> PressInteraction.Cancel(downPress)
                        else -> PressInteraction.Release(downPress)
                    }
                    launch { interactionSource.emit(releaseOrCancel) }
                }
            }
        }
    }
)
