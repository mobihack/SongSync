package pl.lambada.songsync.util

import pl.lambada.songsync.BuildConfig
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.kyant.taglib.TagLib
import pl.lambada.songsync.R
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.data.remote.lyrics_providers.LyricsLookup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import pl.lambada.songsync.util.ext.toLrcFile
import java.io.File
import java.io.FileNotFoundException

fun generateLrcContent(
    song: SongInfo,
    lyrics: String,
    generatedUsingString: String,
    offset: Int = 0,
    directOffset: Boolean
): String {
    val offsetSign = if (offset >= 0) "+" else ""
    val offsetStr = if (!directOffset) "[offset:${offsetSign}${offset}]\n" else ""
    val lyrics = if (directOffset && offset != 0) applyOffsetToLyrics(lyrics, offset) else lyrics

    return "[ti:${song.songName}]\n" +
        "[ar:${song.artistName}]\n" +
        offsetStr +
        "[by:$generatedUsingString]\n" +
        lyrics
}

/**
 * Where lyrics for [song] are saved: beside the song file at [filePath], or in
 * Download/SongSync+ when there is no local file.
 */
fun newLyricsFilePath(filePath: String?, song: SongInfo): File {
    return filePath?.toLrcFile()
        ?: File(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SongSync+"),
            lrcFileName(song)
        )
}

/**
 * "Title - Artist.lrc", with characters a file name can't hold replaced, so a title or artist
 * such as "AC/DC" or "../x" stays one file name instead of becoming a path.
 */
fun lrcFileName(song: SongInfo): String =
    "${song.songName.orEmpty()} - ${song.artistName.orEmpty()}.lrc"
        .replace(Regex("""[/\\:*?"<>|\t\n]"""), "_")
        .let { if (it.startsWith(".")) "_$it" else it }

/**
 * Saves [lrcContent] as the lyrics file for the song at [filePath] (see [newLyricsFilePath]),
 * through the SD card folder [sdCardPath] when direct access isn't allowed.
 *
 * @return the file written
 * @throws Exception when it couldn't be saved; the message says why
 */
fun saveLrcFile(
    context: Context,
    filePath: String?,
    song: SongInfo,
    lrcContent: String,
    sdCardPath: String?,
): File {
    val file = newLyricsFilePath(filePath, song)
    if (isLegacyFileAccessRequired(filePath)) {
        saveToExternalPath(context, filePath, lrcContent, file.name, sdCardPath)
    } else {
        file.parentFile?.mkdirs()
        file.writeText(lrcContent)
    }
    return file
}

/** The MediaStore uri of the audio file at [filePath], or null if the library doesn't have it. */
fun mediaStoreUriFor(context: Context, filePath: String): Uri? {
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
    val selection = "${MediaStore.Files.FileColumns.DATA}=?"

    return context.contentResolver.query(uri, projection, selection, arrayOf(filePath), null)?.use { cursor ->
        val column = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        if (column != -1 && cursor.moveToFirst()) {
            Uri.withAppendedPath(uri, cursor.getLong(column).toString())
        } else null
    }
}

fun getFileDescriptorFromPath(
    context: Context, filePath: String, mode: String = "r"
): ParcelFileDescriptor? {
    val fileUri = mediaStoreUriFor(context, filePath) ?: return null
    return try {
        context.contentResolver.openFileDescriptor(fileUri, mode)
    } catch (e: FileNotFoundException) {
        Log.e("LyricsUtils", "File not found: ${e.message}")
        null
    }
}

/** Where a song file's lyrics already are. */
enum class SavedLyrics { LRC_FILE, EMBEDDED }

/** Tags that hold lyrics in a song file, as TagLib names them. */
private val lyricsTags = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS")

/**
 * Whether the song at [filePath] already has lyrics: an .lrc file beside it (checked first, it's
 * cheap) or lyrics embedded in its tags. Null when it has neither, or the file can't be read.
 * Reads the file, so call it off the main thread.
 */
fun savedLyricsOf(context: Context, filePath: String?): SavedLyrics? {
    if (filePath.isNullOrEmpty()) return null
    if (filePath.toLrcFile()?.exists() == true) return SavedLyrics.LRC_FILE
    return try {
        getFileDescriptorFromPath(context, filePath)?.use { fd ->
            val tags = TagLib.getMetadata(fd.dup().detachFd(), false)?.propertyMap ?: return null
            val embedded = tags.any { (key, values) ->
                key.uppercase().substringBefore(':') in lyricsTags && values.any { it.isNotBlank() }
            }
            if (embedded) SavedLyrics.EMBEDDED else null
        }
    } catch (e: Exception) {
        Log.w("LyricsUtils", "Couldn't read the tags of $filePath: ${e.message}")
        null
    }
}

fun embedLyricsInFile(
    context: Context,
    filePath: String,
    lyrics: String,
    securityExceptionHandler: (PendingIntent) -> Unit = {}
): Boolean {
    return try {
        // "rw", not "w": "w" is write-only and may truncate, which would empty the song file
        // before its tags are read
        val fd = getFileDescriptorFromPath(context, filePath, mode = "rw")
            ?: throw IllegalStateException("File descriptor is null")

        fd.use {
            val metadata = TagLib.getMetadata(it.dup().detachFd(), false) ?: error("Metadata is null")

            TagLib.savePropertyMap(
                it.dup().detachFd(),
                propertyMap = metadata.propertyMap.apply { put("LYRICS", arrayOf(lyrics)) }
            )
        }
    } catch (securityException: SecurityException) {
        Log.e("LyricsUtils", "No permission to embed lyrics: ${securityException.message}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (securityException as? RecoverableSecurityException)
                ?.let { securityExceptionHandler(it.userAction.actionIntent) }
        }
        false
    } catch (e: Exception) {
        Log.e("LyricsUtils", "Error embedding lyrics: ${e.message}")
        false
    }
}

/**
 * Defines possible provider choices
 */
enum class Providers(
    val displayName: String,
    val hasWordByWord: Boolean,
    val enabled: Boolean = true,
) {
    APPLE("Apple Music", true),
    LRCLIB("LRCLib", false),
    // Disabled: search works again (spot-secrets-go is back, checked 2026-11-05), but the
    // lyrics proxy in SpotifyLyricsAPI returns "Lyrics isn't available" for every track.
    SPOTIFY("Spotify", false, enabled = false),
    MUSIXMATCH("Musixmatch", false),
    QQMUSIC("QQ Music", true),
    NETEASE("Netease", true) { val inf = 0 },
    DEEZER("Deezer", false),
    LRCMUX("lrcmux", true),
    KUGOU("KuGou", true),
    BINILYRICS("BiniLyrics", true),
    BETTERLYRICS("BetterLyrics", true),
    GENIE("Genie", false),
    UNISON("Unison", true),
    // Plain-text only; hidden unless a credential is set in local.properties
    GENIUS("Genius", false, enabled = BuildConfig.GENIUS_TOKEN.isNotBlank()),
    PETITLYRICS("PetitLyrics", false, enabled = BuildConfig.PETITLYRICS_CLIENT_ID.isNotBlank()),
    LYRICSOVH("lyrics.ovh", false),
}

/**
 * Finds and saves lyrics for each of [songs] in turn, reporting counts through [onProgressUpdate].
 *
 * A song counts as failed when [findLyrics] throws, or when [saveLyrics] throws or returns false;
 * as having no lyrics when nothing is found. After [RATE_LIMIT_FAILURES] lookups in a row fail,
 * [onRateLimitReached] is called and the download stops. Cancelling the calling coroutine stops it
 * before the next song.
 */
suspend fun downloadLyrics(
    songs: List<Song>,
    findLyrics: suspend (Song) -> LyricsLookup?,
    saveLyrics: suspend (Song, LyricsLookup) -> Boolean,
    onProgressUpdate: (successCount: Int, noLyricsCount: Int, failedCount: Int) -> Unit,
    onDownloadComplete: () -> Unit,
    onRateLimitReached: () -> Unit,
) {
    var successCount = 0
    var noLyricsCount = 0
    var failedCount = 0
    var consecutiveFailures = 0

    for (song in songs) {
        currentCoroutineContext().ensureActive()

        val found = try {
            findLyrics(song).also { consecutiveFailures = 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedCount++
            consecutiveFailures++
            onProgressUpdate(successCount, noLyricsCount, failedCount)
            if (consecutiveFailures >= RATE_LIMIT_FAILURES) return onRateLimitReached()
            continue
        }

        if (found == null) {
            noLyricsCount++
        } else {
            val saved = try {
                saveLyrics(song, found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LyricsUtils", "Couldn't save lyrics for ${song.filePath}: ${e.message}")
                false
            }
            if (saved) successCount++ else failedCount++
        }
        onProgressUpdate(successCount, noLyricsCount, failedCount)
    }

    onDownloadComplete()
}

/** Lookups failing in a row before a batch download assumes it is being rate limited. */
const val RATE_LIMIT_FAILURES = 5

/**
 * Saves batch-downloaded [lyrics] for [song]: embedded in the song file when [embed] is set,
 * otherwise as an .lrc file beside it.
 *
 * @return whether it was saved
 */
fun saveBatchLyrics(
    context: Context,
    song: Song,
    lyrics: LyricsLookup,
    embed: Boolean,
    directOffset: Boolean,
    sdCardPath: String?,
): Boolean {
    val filePath = song.filePath ?: return false
    val lrcContent = generateLrcContent(
        lyrics.songInfo,
        lyrics.lyrics,
        context.getString(R.string.generated_using),
        directOffset = directOffset
    )
    return if (embed) {
        embedLyricsInFile(context, filePath, lrcContent)
    } else {
        saveLrcFile(context, filePath, SongInfo(song.title, song.artist), lrcContent, sdCardPath)
        true
    }
}

fun saveToExternalPath(
    context: Context,
    sourceFilePath: String?,
    lrc: String,
    fileName: String,
    newLyricsFilePath: String?
) {
    val sdRoot = context.externalCacheDirs.getOrNull(1)?.absolutePath?.substringBefore("/Android/data")
        ?: error("No SD card found")
    val lrcPath = sourceFilePath?.toLrcFile()?.absolutePath
        ?: error("path was null when trying to save to sd card")
    if (newLyricsFilePath.isNullOrEmpty()) error("Choose the SD card folder in settings first")

    var folder = DocumentFile.fromTreeUri(context, Uri.parse(newLyricsFilePath))
        ?: error("Can't open the SD card folder")
    for (name in sdCardFolders(lrcPath, sdRoot)) {
        folder = folder.listFiles().firstOrNull { it.isDirectory && it.name == name }
            ?: error("Folder $name not found on the SD card")
    }
    folder.listFiles().firstOrNull { it.name == fileName }?.delete()
    val created = folder.createFile("text/lrc", fileName) ?: error("Couldn't create $fileName")
    context.contentResolver.openOutputStream(created.uri)?.use { it.write(lrc.toByteArray()) }
        ?: error("Couldn't write $fileName")
}

/**
 * The folders from the SD card root [sdRoot] down to the one holding [lrcPath], e.g.
 * "/storage/1234-ABCD/Music/Album/a.lrc" under "/storage/1234-ABCD" gives ["Music", "Album"].
 */
fun sdCardFolders(lrcPath: String, sdRoot: String): List<String> =
    lrcPath.removePrefix(sdRoot).split("/").filter { it.isNotEmpty() }.dropLast(1)

/**
 * "Legacy" way to apply an offset to lyrics, modifies the lyrics string directly
 * as most players do not support the offset tag in LRC files
 * @param lyrics the lyrics to apply the offset to
 * @param offset the offset to apply to the lyrics
 * @return the lyrics with the offset applied
 */
fun applyOffsetToLyrics(lyrics: String, offset: Int): String {
    val timestampRegex = Regex("""[\[<](\d+):(\d+)\.(\d+)[]>]""")

    fun applyOffset(minute: Int, second: Int, millisecond: Int): String {
        val totalMilliseconds = (minute * 60 * 1000) + (second * 1000) + millisecond + offset
        if (totalMilliseconds < 0) return "00:00.000" // Prevent negative times

        // Not wrapped at 60: LRC minutes keep counting in long mixes and audiobooks
        val newMinutes = totalMilliseconds / 60000
        val newSeconds = (totalMilliseconds / 1000) % 60
        val newMilliseconds = (totalMilliseconds % 1000)

        return "${newMinutes.toString().padStart(2, '0')}:" +
                "${newSeconds.toString().padStart(2, '0')}." +
                newMilliseconds.toString().padStart(3, '0')
    }

    return lyrics.replace(timestampRegex) { matchResult ->
        val (minuteStr, secondStr, millisecondStr) = matchResult.destructured
        val minute = minuteStr.toInt()
        val second = secondStr.toInt()
        // The fraction is hundredths (".34") or milliseconds (".345"); read both as milliseconds
        val millisecond = millisecondStr.padEnd(3, '0').take(3).toInt()

        val startChar = matchResult.value[0]
        val endChar = if (startChar == '[') ']' else '>'

        "${startChar}${applyOffset(minute, second, millisecond)}$endChar"
    }
}

/**
 * Splits LRC [lyrics] into (timestamp, text) pairs for display, in time order. Timestamps come out
 * as "mm:ss.mmm". A line with several timestamps gives one pair for each; word-by-word tags and
 * "v1:"/"v2:" speaker marks are left out of the text. Lines without a timestamp are skipped.
 */
fun parseLyrics(lyrics: String): List<Pair<String, String>> {
    val leadingTags = Regex("""^((?:\[\d+:\d+(?:[.:]\d+)?])+)(.*)$""")
    val tag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
    val wordTag = Regex("""<\d+:\d+(?:[.:]\d+)?>""")
    val speaker = Regex("""^v\d+:""")

    return lyrics.lines().flatMap { rawLine ->
        val match = leadingTags.find(rawLine.trim()) ?: return@flatMap emptyList()
        val (tags, rest) = match.destructured
        val text = rest.replace(wordTag, "").replace(speaker, "").trim()

        tag.findAll(tags).map { t ->
            val (minute, second, fraction) = t.destructured
            // ".34" is hundredths, ".345" milliseconds
            val millis = fraction.padEnd(3, '0').take(3)
            val time = minute.toInt() * 60_000L + second.toInt() * 1000L + millis.toInt()
            time to ("${minute.padStart(2, '0')}:${second.padStart(2, '0')}.$millis" to text)
        }.toList()
    }.sortedBy { it.first }.map { it.second }
}

