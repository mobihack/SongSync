package pl.lambada.songsync.build

import io.kotest.matchers.collections.shouldNotContain
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
}
