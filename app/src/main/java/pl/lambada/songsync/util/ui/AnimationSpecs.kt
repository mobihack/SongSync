package pl.lambada.songsync.util.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.ArcMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.ExperimentalAnimationSpecApi
import androidx.compose.animation.core.keyframes
import pl.lambada.songsync.util.ui.MotionConstants.DURATION_ENTER_SHORT

/** Strong ease-out for UI that enters, exits or responds to a tap. */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

val MotionEasingStandard = CubicBezierEasing(0.4F, 0.0F, 0.2F, 1F)

@OptIn(ExperimentalAnimationSpecApi::class, ExperimentalSharedTransitionApi::class)
val SearchFABBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
    keyframes {
        durationMillis = DURATION_ENTER_SHORT
        initialBounds at 0 using ArcMode.ArcBelow using MotionEasingStandard
        targetBounds at DURATION_ENTER_SHORT using ArcMode.ArcAbove using MotionEasingStandard
    }
}

/** Text that ticks upward as the provider fallback advances: in from below, out above. */
fun <S> AnimatedContentTransitionScope<S>.tickerTransform(): ContentTransform =
    (slideInVertically(tween(180, easing = EaseOutStrong)) { it / 2 } +
        fadeIn(tween(180, easing = EaseOutStrong))) togetherWith
        (slideOutVertically(tween(120, easing = EaseOutStrong)) { -it / 2 } +
            fadeOut(tween(120, easing = EaseOutStrong))) using
        SizeTransform(clip = false)
