@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.lyricsFetch.components

import pl.lambada.songsync.ui.components.WithTooltip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import pl.lambada.songsync.util.ui.EaseOutStrong
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.frosted
import pl.lambada.songsync.util.ext.repeatingClickable

/** "+0.3s" / "−1.2s" for an offset in milliseconds. */
fun formatOffset(offsetMs: Int): String =
    (if (offsetMs >= 0) "+" else "−") + "%.1fs".format(java.util.Locale.ROOT, kotlin.math.abs(offsetMs) / 1000.0)

/**
 * The floating toolbar under the lyrics: play and timing on the left, embed and save on the right.
 * Timing shows the offset once it isn't zero, so a shifted file is visible without opening it.
 */
@Composable
fun StageToolbar(
    hazeState: HazeState?,
    /** Null when there's no local file to play. */
    playing: Boolean?,
    onPlayToggle: () -> Unit,
    offsetMs: Int,
    timingOpen: Boolean,
    onTimingToggle: () -> Unit,
    /** Null when there's no local file to embed into. */
    onEmbed: (() -> Unit)?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while a search runs: the toolbar stays, its buttons wait for the lyrics. */
    enabled: Boolean = true,
    /** A tap on the toolbar itself, between its buttons. */
    onBackgroundTap: () -> Unit = {},
) {
    val currentOnBackgroundTap by rememberUpdatedState(onBackgroundTap)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .frosted(hazeState, CircleShape, MaterialTheme.colorScheme.surfaceContainerHigh)
            // Touches between the buttons stay on the toolbar instead of reaching the lyrics below.
            // Seen after the buttons (Final pass): a tap none of them took is a background tap.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    val up = waitForUpOrCancellation(PointerEventPass.Final)
                    if (up != null && !up.isConsumed) currentOnBackgroundTap()
                }
            }
            .padding(8.dp),
    ) {
        if (playing != null) WithTooltip(stringResource(if (playing) R.string.pause_song else R.string.play_song)) {
            FilledIconButton(
                onClick = onPlayToggle,
                enabled = enabled,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.pause_song else R.string.play_song),
                )
            }
        }

        WithTooltip(stringResource(R.string.lyrics_timing)) {
            TimingButton(offsetMs, timingOpen, onTimingToggle, enabled)
        }

        Spacer(Modifier.weight(1f))

        if (onEmbed != null) WithTooltip(stringResource(R.string.embed_lyrics_in_file)) {
            IconButton(onClick = onEmbed, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.AudioFile, contentDescription = stringResource(R.string.embed_lyrics_in_file))
            }
        }
        WithTooltip(stringResource(R.string.save_lrc_file)) {
            Button(
                onClick = onSave,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun TimingButton(offsetMs: Int, open: Boolean, onClick: () -> Unit, enabled: Boolean) {
    val shifted = offsetMs != 0
    val container by animateColorAsState(
        when {
            open -> MaterialTheme.colorScheme.surfaceContainerHighest
            shifted -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "timingButton",
    )
    val offsetLabel = formatOffset(offsetMs)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = if (shifted && !open) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .semantics {
                role = Role.Button
                stateDescription = offsetLabel
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = stringResource(R.string.lyrics_timing), modifier = Modifier.size(20.dp))
            AnimatedVisibility(
                visible = shifted,
                // Fade only: growing the label would relayout the toolbar every frame
                enter = fadeIn(tween(150, easing = EaseOutStrong)),
                exit = fadeOut(tween(100)),
            ) {
                Text(
                    offsetLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * The offset, opened from the toolbar's timing button: step it by 0.1s (hold to repeat) while the
 * song plays, and the highlighted line follows straight away.
 */
@Composable
fun TimingPanel(
    offsetMs: Int,
    onSetOffset: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** What the panel frosts over: the lyrics behind it. */
    hazeState: HazeState? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    // Frosted like the toolbar under it, so the footer reads as one layer over the lyrics
    Surface(
        shape = shape,
        color = Color.Transparent,
        modifier = modifier
            .width(280.dp)
            .frosted(hazeState, shape, MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lyrics_timing),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.lyrics_timing_hold),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(
                    icon = { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.offset_decrease)) },
                    label = stringResource(R.string.lyrics_timing_earlier),
                    onStep = { onSetOffset(offsetMs - 100) },
                )
                Text(
                    formatOffset(offsetMs),
                    // Tabular digits so the value doesn't jitter while stepping
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                StepButton(
                    icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.offset_increase)) },
                    label = stringResource(R.string.lyrics_timing_later),
                    onStep = { onSetOffset(offsetMs + 100) },
                )
            }
            TextButton(
                onClick = { onSetOffset(0) },
                enabled = offsetMs != 0,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.lyrics_timing_reset))
            }
        }
    }
}

@Composable
private fun StepButton(icon: @Composable () -> Unit, label: String, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    // The label says which way the lines move; the button keeps its own spoken name
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = { /* handled by repeatingClickable */ },
            shapes = IconButtonDefaults.shapes(),
            // Shared with repeatingClickable so holding the button still morphs its shape
            interactionSource = interaction,
            modifier = Modifier
                .size(48.dp)
                .repeatingClickable(interactionSource = interaction, enabled = true, maxDelayMillis = 500, onClick = onStep),
            content = icon,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics { },
        )
    }
}
