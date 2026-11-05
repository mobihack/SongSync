package pl.lambada.songsync.build

import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestTest {

    private val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml")).documentElement

    private val permissions: List<String> = manifest.getElementsByTagName("uses-permission").let { nodes ->
        (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }
    }

    @Test
    fun `no notification permission, as the app posts no notifications`() {
        permissions shouldNotContain "android.permission.POST_NOTIFICATIONS"
    }

    private fun element(tag: String, name: String) = manifest.getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) }
            .first { it.attributes.getNamedItem("android:name").nodeValue == name }
    }

    private fun attribute(tag: String, name: String, attribute: String) =
        element(tag, name).attributes.getNamedItem(attribute)?.nodeValue

    @Test
    fun `quick search isn't single instance, so its result reaches the caller`() {
        attribute("activity", ".activities.quicksearch.QuickLyricsSearchActivity", "android:launchMode") shouldBe null
    }

    @Test
    fun `old storage permissions stop where all-files access takes over`() {
        attribute("uses-permission", "android.permission.WRITE_EXTERNAL_STORAGE", "android:maxSdkVersion") shouldBe "29"
        attribute("uses-permission", "android.permission.READ_EXTERNAL_STORAGE", "android:maxSdkVersion") shouldBe "32"
    }
}
