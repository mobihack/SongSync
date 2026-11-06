@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.theme

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
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
 * The colour scheme without dynamic colour (before Android 12, or when it is off). Pure black only
 * applies in dark theme: the setting stays saved while the system is light.
 */
internal fun fallbackColorScheme(darkTheme: Boolean, pureBlack: Boolean): ColorScheme = when {
    darkTheme && pureBlack -> darkColorScheme.pureBlack()
    darkTheme -> darkColorScheme
    else -> lightColorScheme
}

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
                    dynamicDarkColorScheme(context).pureBlack()
                else
                    dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        }

        else -> fallbackColorScheme(darkTheme, pureBlack)
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

/**
 * Pure black: black backgrounds, and every neutral layer on top (cards, sheets, pills, chips,
 * outlines, secondary text) as white at a low opacity over black, so the app is black and white
 * throughout. The accents are white too (black on them), for what is selected or active; only
 * errors keep their colour.
 * The layers are opaque (white composited over black) so they stack like the usual containers.
 */
internal fun ColorScheme.pureBlack(): ColorScheme {
    fun white(alpha: Float) = Color.White.copy(alpha = alpha).compositeOver(Color.Black)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = white(0.04f),
        surfaceContainer = white(0.06f),
        surfaceContainerHigh = white(0.09f),
        surfaceContainerHighest = white(0.12f),
        surfaceBright = white(0.14f),
        surfaceVariant = white(0.12f),
        surfaceTint = Color.White,
        // The accent is white: buttons, selection, switches, labels, progress
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = white(0.88f),
        onPrimaryContainer = Color.Black,
        inversePrimary = Color.Black,
        // The other accents too, so nothing is coloured but errors
        secondary = white(0.8f),
        onSecondary = Color.Black,
        tertiary = Color.White,
        onTertiary = Color.Black,
        tertiaryContainer = white(0.88f),
        onTertiaryContainer = Color.Black,
        // Neutral pills and chips (Get lyrics, unselected toggles) in the same greys
        secondaryContainer = white(0.14f),
        onSecondaryContainer = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        outline = Color.White.copy(alpha = 0.38f),
        outlineVariant = Color.White.copy(alpha = 0.16f),
        scrim = Color.Black,
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
