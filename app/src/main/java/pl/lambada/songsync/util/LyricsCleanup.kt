package pl.lambada.songsync.util

/**
 * Removes lines that aren't lyrics: credit lines ("作词 : …", "Written by: …"),
 * NetEase JSON metadata lines and section labels ("[Chorus]", "[Verse 1: …]").
 */
object LyricsCleanup {
    // Leading "[mm:ss.xx]" line stamps and "<mm:ss.xx>" word stamps
    private val timestamps = Regex("""^(\s*(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]|<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>))+""")
    private val wordStamps = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    private val credit = Regex(
        """^(作词|作詞|作曲|编曲|編曲|词|詞|曲|制作人|監製|监制|混音|录音|和声|合声|合声编写|和声编写|编写|原唱|""" +
            """lyricist|lyrics|lyrics by|written by|writer|composer|composed by|music by|""" +
            """producer|produced by|arranger|arranged by|mixed by|mastered by)\s*[:：]""",
        RegexOption.IGNORE_CASE
    )

    private val sectionLabel = Regex(
        """^\[\s*(intro|verse|pre-chorus|chorus|post-chorus|bridge|hook|refrain|outro|interlude|""" +
            """instrumental|break|drop|skit|spoken|part)\b[^\]]*]$""",
        RegexOption.IGNORE_CASE
    )

    fun clean(lyrics: String?): String? {
        if (lyrics == null) return null
        return lyrics.lines()
            .filterNot { isNoise(it) }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun isNoise(line: String): Boolean {
        val text = line.replace(timestamps, "").replace(wordStamps, "").trim()
        if (text.isEmpty()) return false
        return (text.startsWith("{") && text.endsWith("}")) ||
            credit.containsMatchIn(text) ||
            sectionLabel.matches(text)
    }
}
