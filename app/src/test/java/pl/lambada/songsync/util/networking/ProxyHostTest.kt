package pl.lambada.songsync.util.networking

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.net.InetSocketAddress

class ProxyHostTest {

    @Test
    fun `host names and IPv4 addresses are valid`() {
        listOf("proxy.example.com", "127.0.0.1", "localhost", " 10.0.0.2 ").forEach {
            ProxySettings.isValidHost(it) shouldBe true
        }
    }

    @Test
    fun `IPv6 addresses are valid, bare or in brackets`() {
        listOf("::1", "fe80::1", "[::1]", "[2001:db8::42]").forEach {
            ProxySettings.isValidHost(it) shouldBe true
        }
    }

    @Test
    fun `a port, scheme, path or login typed into the host is invalid`() {
        listOf("127.0.0.1:8080", "proxy:3128", "http://proxy", "http:proxy", "proxy/path", "user@proxy", "")
            .forEach { ProxySettings.isValidHost(it) shouldBe false }
    }

    @Test
    fun `brackets only go around IPv6`() {
        ProxySettings.isValidHost("[proxy.example.com]") shouldBe false
    }

    @Test
    fun `a bracketed IPv6 host is used without its brackets`() {
        val proxy = ProxySettings(mode = ProxyMode.MANUAL, type = ProxyType.HTTP, host = "[::1]", port = 8080).manualProxy()
        (proxy?.address() as InetSocketAddress).hostString shouldBe "::1"
    }
}
