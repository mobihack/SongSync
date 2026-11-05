@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package pl.lambada.songsync.ui.screens.home.components.batchDownload

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.lambada.songsync.R
import pl.lambada.songsync.ui.components.AnimatedText

@Composable
fun DownloadProgressDialog(
    currentSongTitle: String?,
    count: Int,
    total: Int,
    percentage: Int,
    successCount: Int,
    noLyricsCount: Int,
    failedCount: Int,
    onCancel: () -> Unit,
    disableMarquee: Boolean,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(id = R.string.batch_download_lyrics))
        },
        text = {
            Column {
                Text(text = stringResource(R.string.downloading_lyrics))
                AnimatedText(
                    animate = !disableMarquee,
                    text = stringResource(
                        R.string.song,
                        currentSongTitle ?: stringResource(id = R.string.unknown)
                    )
                )
                Text(text = stringResource(R.string.progress, count, total, percentage))
                LinearWavyProgressIndicator(
                    progress = { if (total > 0) count.toFloat() / total else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
                Text(
                    text = stringResource(
                        R.string.success_failed, successCount, noLyricsCount, failedCount
                    )
                )
                Text(text = stringResource(R.string.please_do_not_close_the_app_this_may_take_a_while))
            }
        },
        onDismissRequest = { /* Prevent accidental dismiss */ },
        confirmButton = { /* Empty but required */ },
        dismissButton = {
            OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}