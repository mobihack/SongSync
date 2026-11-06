package pl.lambada.songsync.ui.screens.lyricsFetch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays the song file, so its lyrics can be checked against it while the offset is adjusted.
 * Takes transient audio focus: a music app playing at the time pauses, and resumes afterwards.
 */
@Stable
class LyricsPreviewPlayer(context: Context, private val path: String) {
    var isPlaying by mutableStateOf(false)
        private set

    /** False once the file turned out not to be playable; the play button goes away. */
    var available by mutableStateOf(true)
        private set

    /** Where playback is, in milliseconds; read it every frame while [isPlaying]. */
    val positionMs: Long
        get() = player?.currentPosition?.toLong() ?: 0L

    private var player: MediaPlayer? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) pause() // any loss: a call, another app's playback
    }
    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
        } else null

    /** Plays from [fromMs], or from where it was paused. */
    suspend fun play(fromMs: Long? = null) {
        val player = prepared() ?: return
        if (!requestFocus()) return
        fromMs?.let { player.seekTo(it.coerceAtLeast(0L).toInt()) }
        player.start()
        isPlaying = true
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        if (isPlaying) abandonFocus()
        isPlaying = false
    }

    fun release() {
        pause()
        player?.release()
        player = null
    }

    private suspend fun prepared(): MediaPlayer? {
        player?.let { return it }
        // Preparing reads the file's header: off the main thread
        val created = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(path)
                    prepare()
                }
            }.getOrNull()
        }
        if (created == null) {
            available = false
            return null
        }
        created.setOnCompletionListener {
            isPlaying = false
            abandonFocus()
        }
        player = created
        return created
    }

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }
}

/**
 * A player for the song file at [path], or null when there is no local file to play. It pauses
 * when the screen leaves the foreground and is released with the screen.
 */
@Composable
fun rememberLyricsPreviewPlayer(path: String?): LyricsPreviewPlayer? {
    val context = LocalContext.current
    val player = remember(path) {
        path?.takeIf { it.isNotEmpty() && File(it).isFile }
            ?.let { LyricsPreviewPlayer(context.applicationContext, it) }
    }
    if (player != null) {
        DisposableEffect(player) { onDispose { player.release() } }
        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    }
    return player?.takeIf { it.available }
}
