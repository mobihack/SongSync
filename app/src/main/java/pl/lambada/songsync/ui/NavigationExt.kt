package pl.lambada.songsync.ui

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

/** Whether the screen on top is fully shown: not still animating in, or already leaving. */
private val NavController.isCurrentResumed: Boolean
    get() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

/**
 * Goes back, but only from a screen that is fully shown: extra taps on Back during the exit
 * animation would otherwise pop the screens below too, down to an empty app.
 */
fun NavController.popBackStackOnce() {
    if (isCurrentResumed) popBackStack()
}

/**
 * Opens [route] from a screen that is fully shown, and never on top of itself: a double tap would
 * otherwise stack the same page twice.
 */
fun <T : Any> NavController.navigateOnce(route: T) {
    if (isCurrentResumed) navigate(route) { launchSingleTop = true }
}
