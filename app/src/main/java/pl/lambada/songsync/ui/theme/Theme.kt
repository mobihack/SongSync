@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.theme

import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.ColorScheme
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val darkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val lightColorScheme = expressiveLightColorScheme().copy(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Custom SongSync theme that applies the desired color scheme and system UI adjustments.
 *
 * @param darkTheme Whether to use dark theme based on system settings.
 * @param dynamicColor Whether to use dynamic color scheme (available on Android 12+).
 * @param content The content of the theme.
 */
@Composable
fun SongSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme)
                if (pureBlack)
                    dynamicDarkColorScheme(context).copy(
                        surface = Color.Black,
                        background = Color.Black,
                        surfaceContainerLowest = Color.Black,
                    )
                else
                    dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        }

        pureBlack -> darkColorScheme.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceContainerLowest = Color.Black,
        )

        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }.let { scheme ->
        // Pure black stays black; otherwise lean the background and containers slightly
        // toward the primary colour so the app isn't flat grey
        if (scheme.surface == Color.Black) scheme else scheme.tinted(if (darkTheme) 0.05f else 0.04f)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography,
        content = content,
    )
}

/** Mixes [amount] of primary into the background, surface and surface container roles. */
private fun ColorScheme.tinted(amount: Float): ColorScheme {
    fun Color.tint() = lerp(this, primary, amount)
    return copy(
        background = background.tint(),
        surface = surface.tint(),
        surfaceContainerLowest = surfaceContainerLowest.tint(),
        surfaceContainerLow = surfaceContainerLow.tint(),
        surfaceContainer = surfaceContainer.tint(),
        surfaceContainerHigh = surfaceContainerHigh.tint(),
        surfaceContainerHighest = surfaceContainerHighest.tint(),
    )
}
