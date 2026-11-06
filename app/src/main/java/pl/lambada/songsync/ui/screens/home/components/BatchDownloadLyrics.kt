@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.AnimatedText
import pl.lambada.songsync.ui.components.ConnectedButtonRow
import pl.lambada.songsync.ui.components.FrostedBottomSheet
import pl.lambada.songsync.ui.components.ListGroup
import pl.lambada.songsync.ui.components.SwitchItem
import pl.lambada.songsync.ui.components.connectedButtonShapes
import pl.lambada.songsync.ui.screens.home.HomeViewModel
import kotlin.math.roundToInt

/**
 * Batch download in one frosted sheet, like the app's other sheets: confirm, then progress, then
 * the result. Its step and counts live in [HomeViewModel], so after a rotation the sheet comes
 * back showing the download that is still running. While it runs, the sheet can't be swiped or
 * backed away: only Cancel stops it.
 */
@SuppressLint("StringFormatMatches")
@Composable
fun BatchDownloadLyrics(viewModel: HomeViewModel, hazeState: HazeState?, onDone: () -> Unit) {
    val songs = viewModel.songsToBatchDownload
    val counts = viewModel.batchCounts
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Felt when the run ends. Skips the state the sheet opens in, so reopening after a
    // rotation doesn't repeat it.
    LaunchedEffect(viewModel) {
        snapshotFlow { viewModel.batchUiState }.drop(1).collect { state ->
            when (state) {
                UiState.Done -> haptics.performHapticFeedback(
                    if (viewModel.batchCounts.failed > 0) HapticFeedbackType.Reject else HapticFeedbackType.Confirm
                )
                UiState.RateLimited -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
        }
    }

    val running by rememberUpdatedState(viewModel.batchUiState == UiState.Pending)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !running },
    )

    // Closing stops the download too, not just the sheet
    val close = {
        viewModel.closeBatchDownload()
        onDone()
    }
    // Buttons slide the sheet away first; by then the download is stopped, so it may hide
    val dismiss: () -> Unit = {
        viewModel.closeBatchDownload()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDone() }
    }

    FrostedBottomSheet(
        onDismissRequest = { if (!running) close() },
        hazeState = hazeState,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.batch_download_lyrics),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )

            val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = viewModel.batchUiState,
                transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) using SizeTransform(clip = false) },
                label = "batchStep",
            ) { step ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (step) {
                        UiState.Warning -> {
                            Text(
                                pluralStringResource(R.plurals.this_will_download_lyrics_for_all_songs, songs.size, songs.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val embed = viewModel.userSettingsController.embedLyricsIntoFiles
                            ListGroup(margin = 0.dp) {
                                row {
                                    SwitchItem(
                                        label = stringResource(R.string.embed_lyrics_in_file),
                                        selected = embed,
                                        shapes = it,
                                    ) { viewModel.userSettingsController.updateEmbedLyrics(!embed) }
                                }
                            }
                            ActionRow(
                                secondary = stringResource(R.string.cancel) to dismiss,
                                primary = stringResource(R.string.download) to {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                        viewModel.batchUiState = UiState.LegacyPrompt
                                    } else {
                                        viewModel.batchDownloadLyrics(context)
                                    }
                                },
                            )
                        }

                        UiState.LegacyPrompt -> {
                            Message(stringResource(R.string.set_sd_path_warn))
                            ActionRow(
                                secondary = stringResource(R.string.cancel) to dismiss,
                                primary = stringResource(R.string.ok) to { viewModel.batchDownloadLyrics(context) },
                            )
                        }

                        UiState.Pending -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.downloading_lyrics),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                AnimatedText(
                                    animate = !viewModel.userSettingsController.disableMarquee,
                                    text = songs.getOrNull(counts.done)?.title ?: stringResource(R.string.unknown),
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        stringResource(R.string.batch_progress, counts.done, songs.size),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${counts.percentOf(songs.size)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                LinearWavyProgressIndicator(
                                    progress = { if (songs.isNotEmpty()) counts.done.toFloat() / songs.size else 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Counts(counts)
                            Text(
                                stringResource(R.string.batch_keep_open),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cancel))
                            }
                        }

                        UiState.Done -> {
                            Message(stringResource(R.string.download_complete))
                            Counts(counts)
                            Button(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.ok))
                            }
                        }

                        UiState.RateLimited -> {
                            Message(
                                listOf(
                                    stringResource(R.string.spotify_api_rate_limit_reached),
                                    stringResource(R.string.please_try_again_later),
                                    stringResource(R.string.change_api_strategy),
                                ).joinToString(" ")
                            )
                            Button(onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** Cancel and the step's main action as a connected pair, the main one filled. */
@Composable
private fun ActionRow(secondary: Pair<String, () -> Unit>, primary: Pair<String, () -> Unit>) =
    ConnectedButtonRow(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = secondary.second,
            shapes = connectedButtonShapes(0, 2),
            modifier = Modifier.weight(1f),
        ) { Text(secondary.first) }
        Button(
            onClick = primary.second,
            shapes = connectedButtonShapes(1, 2),
            modifier = Modifier.weight(1f),
        ) { Text(primary.first) }
    }

/** Saved / no lyrics / failed so far, as three tiles. */
@Composable
private fun Counts(counts: BatchCounts) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CountTile(counts.success, stringResource(R.string.batch_saved), Modifier.weight(1f))
    CountTile(counts.noLyrics, stringResource(R.string.batch_no_lyrics), Modifier.weight(1f))
    CountTile(
        counts.failed,
        stringResource(R.string.batch_failed),
        Modifier.weight(1f),
        // Failures stand out once there are any
        highlight = counts.failed > 0,
    )
}

@Composable
private fun CountTile(count: Int, label: String, modifier: Modifier, highlight: Boolean = false) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    // A step above the sheet's frosted tint, so the tiles read as their own surfaces
    color = if (highlight) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = if (highlight) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text("$count", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

enum class UiState {
    Warning, LegacyPrompt, Pending, Done, RateLimited
}

/** Songs a batch download has finished so far, by outcome. */
data class BatchCounts(val success: Int = 0, val noLyrics: Int = 0, val failed: Int = 0) {
    val done: Int get() = success + noLyrics + failed

    /** Progress through [total] songs as a whole percentage; 0 for an empty batch. */
    fun percentOf(total: Int): Int = if (total > 0) (done * 100f / total).roundToInt() else 0
}
