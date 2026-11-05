package pl.lambada.songsync.data.remote

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Android blocks plain http unless the network security config allows the host, and MockEngine
 * tests never notice. Every http:// URL in the app's code must have its host allowed there.
 */
class CleartextTrafficTest {

    private val main = File("src/main")

    private fun xml(file: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    private val httpUrl = Regex(""""(http://[^"$]+)"""")

    @Test
    fun `the manifest uses the network security config`() {
        xml(File(main, "AndroidManifest.xml"))
            .getElementsByTagName("application").item(0)
            .attributes.getNamedItem("android:networkSecurityConfig")?.nodeValue shouldBe
            "@xml/network_security_config"
    }

    @Test
    fun `every plain http host the code calls is allowed`() {
        val config = xml(File(main, "res/xml/network_security_config.xml"))
        val domains = config.getElementsByTagName("domain")
        val allowed = (0 until domains.length).map { domains.item(it).textContent.trim() }

        val hosts = File(main, "java").walk()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filterNot { it.trimStart().let { line -> line.startsWith("*") || line.startsWith("//") } } // comments
                    .flatMap { line -> httpUrl.findAll(line).map { URI(it.groupValues[1]).host } }
            }
            .filterNot { it == "apache.org" } // XML parser feature names, not requests
            .toSet()

        hosts shouldBe setOf("mobilecdn.kugou.com") // update both lists when adding one
        allowed shouldContainAll hosts
    }
}
