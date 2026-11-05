package pl.lambada.songsync.util.networking

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Test
import pl.lambada.songsync.util.Providers
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class AppProxyTest {
    private val uri = URI("https://lrclib.net/api/get")
    private val socks = ProxySettings(mode = ProxyMode.MANUAL, type = ProxyType.SOCKS, host = "127.0.0.1", port = 1080)

    @After
    fun reset() = AppProxy.update(ProxySettings(), pool = null)

    private fun selected(settings: ProxySettings, provider: Providers? = Providers.LRCLIB): Proxy {
        AppProxy.update(settings, pool = null)
        return AppProxy.selectorFor(provider).select(uri).single()
    }

    @Test
    fun `off connects directly`() {
        selected(ProxySettings(mode = ProxyMode.OFF)) shouldBe Proxy.NO_PROXY
    }

    @Test
    fun `manual socks and http give the right proxy`() {
        selected(socks).run {
            type() shouldBe Proxy.Type.SOCKS
            (address() as InetSocketAddress).port shouldBe 1080
        }
        selected(socks.copy(type = ProxyType.HTTP, host = "proxy.example", port = 8080)).run {
            type() shouldBe Proxy.Type.HTTP
            (address() as InetSocketAddress).hostString shouldBe "proxy.example"
            (address() as InetSocketAddress).port shouldBe 8080
        }
    }

    @Test
    fun `invalid manual settings fail closed instead of connecting directly`() {
        listOf(socks.copy(host = ""), socks.copy(host = "a b"), socks.copy(port = 0), socks.copy(port = 70000)).forEach {
            val proxy = selected(it)
            proxy shouldNotBe Proxy.NO_PROXY
            proxy shouldBe AppProxy.unreachable
        }
    }

    @Test
    fun `system mode follows the default selector`() {
        // The JVM default selector has no proxy configured in tests
        selected(ProxySettings(mode = ProxyMode.SYSTEM)) shouldBe Proxy.NO_PROXY
    }

    @Test
    fun `chosen scope proxies only the chosen providers`() {
        val chosen = socks.copy(scope = ProxyScope.CHOSEN, providers = setOf(Providers.MUSIXMATCH))
        selected(chosen, Providers.MUSIXMATCH).type() shouldBe Proxy.Type.SOCKS
        selected(chosen, Providers.LRCLIB) shouldBe Proxy.NO_PROXY
        // Update check and cover art aren't a provider
        selected(chosen, provider = null) shouldBe Proxy.NO_PROXY
    }

    @Test
    fun `all scope proxies everything, including non-provider requests`() {
        selected(socks, provider = null).type() shouldBe Proxy.Type.SOCKS
    }

    @Test
    fun `a change applies to a selector made before it`() {
        val selector = AppProxy.selectorFor(Providers.APPLE)
        selector.select(uri).single() shouldBe Proxy.NO_PROXY
        AppProxy.update(socks, pool = null)
        selector.select(uri).single().type() shouldBe Proxy.Type.SOCKS
    }

    private fun proxyAuthRequired(request: Request = Request.Builder().url("https://lrclib.net/").build()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(407).message("Proxy Authentication Required").build()

    @Test
    fun `authenticator sends basic credentials once for an http proxy`() {
        val http = socks.copy(type = ProxyType.HTTP, username = "user", password = "pass")
        val auth = AppProxy.authenticator { http }

        val retried = auth.authenticate(null, proxyAuthRequired())!!
        retried.header("Proxy-Authorization")!! shouldStartWith "Basic "

        // Refused again with the credentials: don't loop
        auth.authenticate(null, proxyAuthRequired(retried)) shouldBe null
    }

    @Test
    fun `authenticator does nothing without a username or for socks`() {
        AppProxy.authenticator { socks.copy(type = ProxyType.HTTP) }.authenticate(null, proxyAuthRequired()) shouldBe null
        AppProxy.authenticator { socks.copy(username = "user", password = "pass") }
            .authenticate(null, proxyAuthRequired()) shouldBe null
    }

    @Test
    fun `test reports a failure when nothing listens on the proxy port`() {
        val closed = socks.copy(type = ProxyType.HTTP, port = 1)
        AppProxy.test(closed, base = OkHttpClient()).isFailure shouldBe true
    }
}
