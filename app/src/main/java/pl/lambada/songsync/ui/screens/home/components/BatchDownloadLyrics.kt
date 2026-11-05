package pl.lambada.songsync.ui.screens.home.components

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import pl.lambada.songsync.ui.screens.home.HomeViewModel
import pl.lambada.songsync.ui.screens.home.components.batchDownload.BatchDownloadWarningDialog
import pl.lambada.songsync.ui.screens.home.components.batchDownload.DownloadCompleteDialog
import pl.lambada.songsync.ui.screens.home.components.batchDownload.DownloadProgressDialog
import pl.lambada.songsync.ui.screens.home.components.batchDownload.LegacyPromptDialog
import pl.lambada.songsync.ui.screens.home.components.batchDownload.RateLimitedDialog
import kotlin.math.roundToInt

/**
 * The batch download dialogs. Their step and counts live in [HomeViewModel], so after a rotation
 * the dialog comes back showing the download that is still running.
 */
@SuppressLint("StringFormatMatches")
@Composable
fun BatchDownloadLyrics(viewModel: HomeViewModel, onDone: () -> Unit) {
    val songs = viewModel.songsToBatchDownload
    val counts = viewModel.batchCounts
    val context = LocalContext.current

    // Closing any dialog stops the download too, not just the dialog
    val close = {
        viewModel.closeBatchDownload()
        onDone()
    }

    when (viewModel.batchUiState) {
        UiState.Warning -> BatchDownloadWarningDialog(
            songsCount = songs.size,
            onConfirm = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    viewModel.batchUiState = UiState.LegacyPrompt
                } else {
                    viewModel.batchDownloadLyrics(context)
                }
            },
            onDismiss = close,
            embedLyrics = viewModel.userSettingsController.embedLyricsIntoFiles,
            onEmbedLyricsChangeRequest = viewModel.userSettingsController::updateEmbedLyrics,
        )

        UiState.LegacyPrompt -> LegacyPromptDialog(
            onConfirm = { viewModel.batchDownloadLyrics(context) },
            onDismiss = close,
        )

        UiState.Pending -> DownloadProgressDialog(
            currentSongTitle = songs.getOrNull(counts.done)?.title,
            count = counts.done,
            total = songs.size,
            percentage = counts.percentOf(songs.size),
            successCount = counts.success,
            noLyricsCount = counts.noLyrics,
            failedCount = counts.failed,
            onCancel = close,
            disableMarquee = viewModel.userSettingsController.disableMarquee
        )

        UiState.Done -> DownloadCompleteDialog(
            successCount = counts.success,
            noLyricsCount = counts.noLyrics,
            failedCount = counts.failed,
            onDismiss = close,
        )

        UiState.RateLimited -> RateLimitedDialog(onDismiss = close)
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
