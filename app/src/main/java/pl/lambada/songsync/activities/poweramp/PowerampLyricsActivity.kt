package pl.lambada.songsync.activities.poweramp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.lambada.songsync.R
import pl.lambada.songsync.data.UserSettingsController
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.services.PowerampApi
import pl.lambada.songsync.services.PowerampLyricsStore
import pl.lambada.songsync.services.PowerampTrackPath
import pl.lambada.songsync.ui.theme.SongSyncTheme
import pl.lambada.songsync.util.dataStore
import pl.lambada.songsync.util.embedLyricsInFile
import pl.lambada.songsync.util.generateLrcContent
import pl.lambada.songsync.util.isLegacyFileAccessRequired
import pl.lambada.songsync.util.newLyricsFilePath
import pl.lambada.songsync.util.saveToExternalPath
import pl.lambada.songsync.util.showToast

/**
 * Opened by Poweramp when the user taps SongSync's link under lyrics it supplied
 * (ACTION_LYRICS_LINK). Shows those lyrics with buttons to save them as .lrc or embed them.
 */
class PowerampLyricsActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = UserSettingsController(dataStore)
        val entry = realId(intent)?.let { PowerampLyricsStore.load(this, it) }?.let { stored ->
            // Entries stored without a file: look it up now from the link's track details
            if (stored.path != null) stored
            else stored.copy(
                path = PowerampTrackPath.resolve(
                    this, intent.getStringExtra(EXTRA_PATH), stored.title, stored.artist,
                    intent.getIntExtra(EXTRA_DURATION_MS, 0).takeIf { it > 0 }?.toLong(),
                )
            )
        }
        if (entry == null) {
            showToast(this, R.string.poweramp_lyrics_not_found)
            finish()
            return
        }

        setContent {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }

            fun run(action: suspend () -> Unit) {
                if (busy) return
                busy = true
                scope.launch {
                    action()
                    busy = false
                }
            }

            SongSyncTheme(pureBlack = settings.pureBlack) {
                ModalBottomSheet(sheetState = sheetState, onDismissRequest = { finish() }) {
                    PowerampLyricsSheet(
                        entry = entry,
                        busy = busy,
                        onSave = { run { saveLrc(entry, settings) } },
                        onEmbed = { run { embed(entry, settings) } },
                    )
                }
            }
        }
    }

    private fun realId(intent: Intent): Long? {
        // Track fields come as extras; also accept them bundled under "track"
        val id = intent.getLongExtra(EXTRA_REAL_ID, 0L).takeIf { it != 0L }
            ?: intent.getBundleExtra(EXTRA_TRACK)?.getLong(EXTRA_REAL_ID, 0L)?.takeIf { it != 0L }
        return id
    }

    private fun lrcContent(entry: PowerampLyricsStore.Entry, settings: UserSettingsController) =
        generateLrcContent(
            SongInfo(entry.title, entry.artist),
            entry.lyrics,
            getString(R.string.generated_using),
            directOffset = settings.directlyModifyTimestamps,
        )

    private suspend fun saveLrc(entry: PowerampLyricsStore.Entry, settings: UserSettingsController) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = newLyricsFilePath(entry.path, SongInfo(entry.title, entry.artist))
                val content = lrcContent(entry, settings)
                if (isLegacyFileAccessRequired(entry.path)) saveToExternalPath(
                    context = this@PowerampLyricsActivity,
                    sourceFilePath = entry.path,
                    lrc = content,
                    fileName = file.name,
                    newLyricsFilePath = settings.sdCardPath,
                ) else file.writeText(content)
                file.absolutePath
            }
        }
        result
            .onSuccess {
                if (settings.powerampRescanAfterSave) entry.path?.let { path -> PowerampApi.rescanFolderOf(this, path) }
                showToast(this, R.string.file_saved_to, it)
                finish()
            }
            .onFailure { showToast(this, it.message ?: getString(R.string.error)) }
    }

    private suspend fun embed(entry: PowerampLyricsStore.Entry, settings: UserSettingsController) {
        val path = entry.path ?: return showToast(this, R.string.embed_non_local_song_error)
        val embedded = withContext(Dispatchers.IO) {
            embedLyricsInFile(this@PowerampLyricsActivity, path, lrcContent(entry, settings))
        }
        if (embedded) {
            if (settings.powerampRescanAfterSave) PowerampApi.rescanFolderOf(this, path)
            showToast(this, R.string.embedded_lyrics_in_file)
            finish()
        } else {
            showToast(this, R.string.poweramp_embed_failed)
        }
    }

    private companion object {
        const val EXTRA_REAL_ID = "realId"
        const val EXTRA_TRACK = "track"
        const val EXTRA_PATH = "path"
        const val EXTRA_DURATION_MS = "durMs"
    }
}

@androidx.compose.runtime.Composable
private fun PowerampLyricsSheet(
    entry: PowerampLyricsStore.Entry,
    busy: Boolean,
    onSave: () -> Unit,
    onEmbed: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(entry.artist, "Lyrics from ${entry.provider}").joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                remember(entry.lyrics) { previewText(entry.lyrics) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }

        entry.path?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onEmbed,
                enabled = !busy && entry.path != null,
                modifier = Modifier.weight(1f),
            ) { Text(androidx.compose.ui.res.stringResource(R.string.embed_lyrics_in_file)) }
            Button(
                onClick = onSave,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(androidx.compose.ui.res.stringResource(R.string.save_lrc_file)) }
        }
    }
}

private val timeTag = Regex("""[\[<]\d+:\d+(?:[.:]\d+)?[\]>]""")

/** Lyrics as plain lines for reading: timestamps and word timings removed. */
private fun previewText(lyrics: String): String = lyrics.lines()
    .map { it.replace(timeTag, "").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")
