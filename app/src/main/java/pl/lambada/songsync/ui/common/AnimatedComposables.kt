package pl.lambada.songsync.ui.common

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pl.lambada.songsync.util.ui.EmphasizedDecelerate
import pl.lambada.songsync.util.ui.MotionConstants.InitialOffset
import pl.lambada.songsync.util.ui.materialSharedAxisXIn
import pl.lambada.songsync.util.ui.materialSharedAxisXOut

inline fun <reified T : Any> NavGraphBuilder.animatedComposable(
    deepLinks: List<NavDeepLink> = emptyList(),
    usePredictiveBack: Boolean = Build.VERSION.SDK_INT >= 34,
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) {
    if (usePredictiveBack) {
        animatedComposablePredictiveBack<T>(deepLinks, content)
    } else {
        animatedComposableLegacy<T>(deepLinks, content)
    }
}

inline fun <reified T : Any> NavGraphBuilder.animatedComposablePredictiveBack(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        enterTransition = { materialSharedAxisXIn(initialOffsetX = { (it * 0.15f).toInt() }) },
        exitTransition = {
            materialSharedAxisXOut(targetOffsetX = { -(it * InitialOffset).toInt() })
        },
        popEnterTransition = {
            scaleIn(
                animationSpec = tween(durationMillis = 250, easing = EmphasizedDecelerate),
                initialScale = 0.95f,
            ) + materialSharedAxisXIn(initialOffsetX = { -(it * InitialOffset).toInt() })
        },
        popExitTransition = {
            materialSharedAxisXOut(targetOffsetX = { (it * InitialOffset).toInt() }) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(durationMillis = 250, easing = EmphasizedDecelerate),
                    )
        },
        content = content,
    )

inline fun <reified T : Any> NavGraphBuilder.animatedComposableLegacy(
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable<T>(
        deepLinks = deepLinks,
        enterTransition = {
            materialSharedAxisXIn(initialOffsetX = { (it * InitialOffset).toInt() })
        },
        exitTransition = {
            materialSharedAxisXOut(targetOffsetX = { -(it * InitialOffset).toInt() })
        },
        popEnterTransition = {
            materialSharedAxisXIn(initialOffsetX = { -(it * InitialOffset).toInt() })
        },
        popExitTransition = {
            materialSharedAxisXOut(targetOffsetX = { (it * InitialOffset).toInt() })
        },
        content = content,
    )
