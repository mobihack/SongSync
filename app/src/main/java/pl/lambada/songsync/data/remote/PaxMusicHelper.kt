package pl.lambada.songsync.data.remote

import pl.lambada.songsync.domain.model.lyrics_providers.PaxLyrics
import pl.lambada.songsync.domain.model.lyrics_providers.PaxLyricsLineDetails
import pl.lambada.songsync.domain.model.lyrics_providers.PaxResponse
import pl.lambada.songsync.util.ext.toLrcTimestamp
import pl.lambada.songsync.util.networking.Ktor.json

class PaxMusicHelper {

    /**
     * Formats syllable lyrics into LRC format.
     * @param lyrics The list of PaxLyrics objects.
     * @param multiPersonWordByWord Flag to format lyrics for multiple persons word by word.
     * @return The formatted lyrics as a string.
     */
    private fun formatSyllableLyrics(lyrics: List<PaxLyrics>, multiPersonWordByWord: Boolean): String {
        val syncedLyrics = StringBuilder()
        for (line in lyrics) {
            syncedLyrics.append("[${line.timestamp.toLrcTimestamp()}]")

            if (multiPersonWordByWord) {
                syncedLyrics.append(
                    if (line.oppositeTurn) "v2:" else "v1:"
                )
            }

            appendSyllables(syncedLyrics, line.text)

            if (line.background && multiPersonWordByWord) {
                syncedLyrics.append("\n[bg:")
                appendSyllables(syncedLyrics, line.backgroundText)
                syncedLyrics.append("]")
            }
            syncedLyrics.append("\n")
        }
        return syncedLyrics.toString()
    }

    /**
     * Appends each syllable with its start and end word tags. A tag whose time is missing is left
     * out, rather than written as "<null>".
     */
    private fun appendSyllables(out: StringBuilder, syllables: List<PaxLyricsLineDetails>) {
        for (syllable in syllables) {
            syllable.timestamp?.let {
                val begin = "<${it.toLrcTimestamp()}>"
                if (!out.endsWith(begin)) out.append(begin)
            }
            out.append(syllable.text)
            if (!syllable.part) out.append(" ")
            syllable.endtime?.let { out.append("<${it.toLrcTimestamp()}>") }
        }
    }

    /**
     * Formats line lyrics into LRC format.
     * @param lyrics The list of PaxLyrics objects.
     * @return The formatted lyrics as a string.
     */
    private fun formatLineLyrics(lyrics: List<PaxLyrics>): String {
        val syncedLyrics = StringBuilder()
        for (line in lyrics) {
            // An empty line (an instrumental break) has no text parts
            syncedLyrics.append("[${line.timestamp.toLrcTimestamp()}]${line.text.joinToString("") { it.text }}\n")
        }
        return syncedLyrics.toString()
    }

    /**
     * Formats word-by-word lyrics into LRC format.
     * @param apiResponse The API response containing the lyrics.
     * @param multiPersonWordByWord Flag to format lyrics for multiple persons word by word.
     * @return The formatted lyrics as a string or null if the lyrics were not found or the
     * response couldn't be read.
     */
    fun formatWordByWordLyrics(apiResponse: String, multiPersonWordByWord: Boolean): String? {
        return try {
            val data = json.decodeFromString<PaxResponse>(apiResponse)
            val lines = data.content
            if (lines.isNullOrEmpty())
                return null

            when (data.type) {
                "Syllable" -> formatSyllableLyrics(lines, multiPersonWordByWord).dropLast(1)
                "Line" -> formatLineLyrics(lines).dropLast(1)
                else -> null
            }
        } catch (e: Exception) {
            // Older responses are a bare list of syllable lines; anything else (an error page,
            // an unexpected shape) means no lyrics
            runCatching {
                val data = json.decodeFromString<List<PaxLyrics>>(apiResponse)
                if (data.isEmpty()) null else formatSyllableLyrics(data, multiPersonWordByWord)
            }.getOrNull()
        }
    }
}
