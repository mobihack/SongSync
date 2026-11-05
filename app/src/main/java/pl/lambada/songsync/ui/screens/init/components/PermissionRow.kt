@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.init.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.listGroupColors

/** Strong ease-out, so the status change lands quickly and settles. */
private val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private enum class PermissionState { NOT_GRANTED, REQUESTING, GRANTED }

/**
 * A permission on the welcome screen, drawn as a row of a [pl.lambada.songsync.ui.components.ListGroup]:
 * shaped tonal icon, title and why it's needed, then Allow, a spinner while the request is open,
 * or a check once granted.
 */
@Composable
fun PermissionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
    granted: Boolean,
    requesting: Boolean,
    onRequest: () -> Unit,
    shapes: ListItemShapes,
) {
    val state = when {
        granted -> PermissionState.GRANTED
        requesting -> PermissionState.REQUESTING
        else -> PermissionState.NOT_GRANTED
    }
    SegmentedListItem(
        onClick = { if (state == PermissionState.NOT_GRANTED) onRequest() },
        shapes = shapes,
        colors = listGroupColors(),
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(200, easing = EaseOutStrong)) + scaleIn(tween(200, easing = EaseOutStrong), initialScale = 0.9f))
                        .togetherWith(fadeOut(tween(120)))
                },
                contentAlignment = Alignment.Center,
                label = "permissionState",
            ) { s ->
                when (s) {
                    PermissionState.NOT_GRANTED -> FilledTonalButton(onClick = onRequest) { Text(stringResource(R.string.setup_allow)) }
                    PermissionState.REQUESTING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    PermissionState.GRANTED -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.setup_allowed),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    }
}
