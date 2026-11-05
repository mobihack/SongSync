package pl.lambada.songsync.util

import pl.lambada.songsync.util.ext.toLrcTimestamp

/**
 * Word-by-word (enhanced) LRC shared by providers that return per-word timings.
 * Output matches the format SongSync already produces for Apple Music and QQ Music:
 * `[mm:ss.mmm]<mm:ss.mmm>word <mm:ss.mmm>word<mm:ss.mmm>`
 */
object WordSync {
    data class Word(val text: String, val startMs: Long, val endMs: Long)
    data class Line(val startMs: Long, val words: List<Word>)

    fun render(lines: List<Line>): String = lines
        .filter { it.words.isNotEmpty() }
        .joinToString("\n") { line ->
            buildString {
                append("[${line.startMs.toInt().toLrcTimestamp()}]")
                for (word in line.words) {
                    val start = "<${word.startMs.toInt().toLrcTimestamp()}>"
                    if (!endsWith(start)) append(start)
                    append(word.text)
                    append("<${word.endMs.toInt().toLrcTimestamp()}>")
                }
            }
        }

    private val yrcLine = Regex("""^\[(\d+),\d+](.*)$""")
    private val yrcWord = Regex("""\((\d+),(\d+),-?\d+\)([^(]*)""")

    /**
     * Parses NetEase "yrc": `[lineStart,lineDuration](wordStart,wordDuration,0)word...`,
     * all in absolute milliseconds. JSON metadata lines are skipped.
     */
    fun parseYrc(yrc: String): List<Line> = yrc.lines().mapNotNull { raw ->
        val match = yrcLine.find(raw.trim()) ?: return@mapNotNull null
        val words = yrcWord.findAll(match.groupValues[2]).map {
            val start = it.groupValues[1].toLong()
            Word(it.groupValues[3], start, start + it.groupValues[2].toLong())
        }.toList()
        Line(match.groupValues[1].toLong(), words)
    }
}
