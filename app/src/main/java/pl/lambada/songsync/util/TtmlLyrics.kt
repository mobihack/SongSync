package pl.lambada.songsync.util

import org.w3c.dom.Element
import org.w3c.dom.Node
import pl.lambada.songsync.util.ext.toLrcTimestamp
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Apple-style TTML lyrics (BiniLyrics, BetterLyrics, Unison) to SongSync's word-by-word LRC,
 * or line LRC when the TTML only has line timings. Background vocals (`x-bg`) are dropped.
 */
object TtmlLyrics {
    /** The LRC for [ttml], or null if it doesn't parse or has no timed lines. */
    fun toLrc(ttml: String): String? =
        runCatching { convert(ttml) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun convert(ttml: String): String {
        val doc = DocumentBuilderFactory.newInstance().apply {
            // Don't resolve external entities from a remote document. Android's parser rejects
            // this feature (and doesn't resolve external entities anyway), so it's best-effort.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newDocumentBuilder().parse(ttml.byteInputStream())

        val paragraphs = doc.getElementsByTagName("p")
        val lines = (0 until paragraphs.length).mapNotNull { paragraphs.item(it) as? Element }
        val wordTimed = lines.any { p -> p.childElements().any { it.tagName == "span" && it.hasAttribute("begin") } }

        if (!wordTimed) {
            return lines.mapNotNull { p ->
                val start = parseTime(p.getAttribute("begin")) ?: return@mapNotNull null
                "[${start.toInt().toLrcTimestamp()}]${p.textContent.trim()}"
            }.joinToString("\n")
        }

        return WordSync.render(lines.mapNotNull { p ->
            val start = parseTime(p.getAttribute("begin")) ?: return@mapNotNull null
            val words = mutableListOf<WordSync.Word>()
            for (node in p.childNodes.asList()) {
                when {
                    node is Element && node.tagName == "span" && !node.isBackground() -> {
                        val begin = parseTime(node.getAttribute("begin")) ?: continue
                        val end = parseTime(node.getAttribute("end")) ?: begin
                        words += WordSync.Word(node.textContent, begin, end)
                    }
                    // Spaces between word spans belong to the previous word
                    node.nodeType == Node.TEXT_NODE && words.isNotEmpty() && node.textContent.isNotEmpty() -> {
                        val last = words.removeAt(words.lastIndex)
                        words += last.copy(text = last.text + node.textContent.replace(Regex("""\s+"""), " "))
                    }
                }
            }
            WordSync.Line(start, words)
        })
    }

    private fun Element.isBackground() =
        getAttribute("ttm:role") == "x-bg" || getAttribute("role") == "x-bg"

    private fun Element.childElements() = childNodes.asList().filterIsInstance<Element>()

    private fun org.w3c.dom.NodeList.asList() = (0 until length).map { item(it) }

    /** TTML clock values: "12.345", "1:04.080" or "1:02:03.5", in seconds. */
    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val seconds = value.trim().removeSuffix("s").split(':')
            .fold(0.0) { acc, part -> acc * 60 + (part.toDoubleOrNull() ?: return null) }
        return Math.round(seconds * 1000)
    }
}
