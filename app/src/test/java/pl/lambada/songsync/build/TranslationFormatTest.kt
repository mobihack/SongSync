package pl.lambada.songsync.build

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Translations must use the same placeholders as the English strings: a wrong type or position
 * crashes String.format at runtime, and a missing count silently drops the number.
 */
class TranslationFormatTest {

    private val res = File("src/main/res")
    private val placeholder = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])""")

    /** Each placeholder as "position:type", with unnumbered ones numbered in order. */
    private fun placeholders(text: String): Set<String> {
        var next = 1
        return placeholder.findAll(text.replace("%%", "")).map {
            val position = it.groupValues[1].ifEmpty { (next++).toString() }
            "$position:${it.groupValues[2]}"
        }.toSet()
    }

    private class Entries(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun read(file: File): Entries {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        fun elements(tag: String) = root.getElementsByTagName(tag).let { n -> (0 until n.length).map { n.item(it) as Element } }
        return Entries(
            strings = elements("string").associate { it.getAttribute("name") to it.textContent },
            plurals = elements("plurals").associate { plural ->
                plural.getAttribute("name") to plural.getElementsByTagName("item").let { n ->
                    (0 until n.length).map { n.item(it) as Element }.associate { it.getAttribute("quantity") to it.textContent }
                }
            },
        )
    }

    private val english = read(File(res, "values/strings.xml"))
    private val translations = res.listFiles()!!
        .filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
        .associate { it.name.removePrefix("values-") to read(File(it, "strings.xml")) }

    @Test
    fun `translated strings use the English placeholders`() {
        val problems = translations.flatMap { (locale, t) ->
            t.strings.mapNotNull { (name, text) ->
                val expected = english.strings[name]?.let(::placeholders) ?: return@mapNotNull null
                val actual = placeholders(text)
                if (actual != expected) "$locale/$name: $actual, English has $expected" else null
            }
        }
        problems.joinToString("\n") shouldBe ""
    }

    @Test
    fun `translated plurals keep the count and use no other placeholders`() {
        val problems = translations.flatMap { (locale, t) ->
            t.plurals.flatMap { (name, items) ->
                val expected = english.plurals[name]?.values?.flatMap(::placeholders)?.toSet() ?: return@flatMap emptyList()
                items.mapNotNull { (quantity, text) ->
                    val actual = placeholders(text)
                    when {
                        !expected.containsAll(actual) -> "$locale/$name[$quantity]: $actual, English has $expected"
                        // "zero", "one" and "two" may be words ("no songs", "a song", Arabic's dual);
                        // every other form shows the number
                        quantity !in setOf("zero", "one", "two") && actual != expected ->
                            "$locale/$name[$quantity] drops ${expected - actual}"
                        else -> null
                    }
                }
            }
        }
        problems.joinToString("\n") shouldBe ""
    }
}
