package pl.lambada.songsync.util.networking

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import pl.lambada.songsync.util.Providers
import java.util.concurrent.TimeUnit

/**
 * Requests go through a real Ktor client, built the way [Ktor.clientFor] builds them, to a
 * MockWebServer standing in for an HTTP proxy. A proxied request arrives with the full URL in its
 * request line ("GET http://host/path"), which is how the server tells it came via the proxy.
 */
class ProxyRoutingTest {
    private lateinit var proxyServer: MockWebServer
    private val target = "http://lyrics.invalid/api/get"

    @Before
    fun setUp() {
        proxyServer = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        proxyServer.shutdown()
        AppProxy.update(ProxySettings(), pool = Ktor.connectionPool)
    }

    private fun viaServer(username: String = "", scope: ProxyScope = ProxyScope.ALL, providers: Set<Providers> = emptySet()) =
        ProxySettings(
            mode = ProxyMode.MANUAL,
            type = ProxyType.HTTP,
            host = proxyServer.hostName,
            port = proxyServer.port,
            username = username,
            password = "secret",
            scope = scope,
            providers = providers,
        )

    @Test
    fun `manual http proxy carries provider requests`() = runTest {
        AppProxy.update(viaServer())
        proxyServer.enqueue(MockResponse().setBody("via proxy"))

        Ktor.clientFor(Providers.LRCLIB).get(target).bodyAsText() shouldBe "via proxy"

        proxyServer.takeRequest(1, TimeUnit.SECONDS)!!.requestLine shouldBe "GET $target HTTP/1.1"
    }

    @Test
    fun `switching to off stops using the proxy without rebuilding the client`() = runTest {
        val client = Ktor.clientFor(Providers.DEEZER)
        AppProxy.update(viaServer())
        proxyServer.enqueue(MockResponse().setBody("ok"))
        client.get(target).bodyAsText() shouldBe "ok"
        // Kept alive to the proxy...
        Ktor.connectionPool.connectionCount() shouldBe 1

        AppProxy.update(ProxySettings(mode = ProxyMode.OFF), evictOn = { it.run() })
        // ...and dropped on the change, so it can't be reused for a direct request
        Ktor.connectionPool.connectionCount() shouldBe 0
        // Direct now: lyrics.invalid doesn't resolve
        runCatching { client.get(target) }.isFailure shouldBe true
        proxyServer.requestCount shouldBe 1
    }

    @Test
    fun `chosen scope skips the proxy for other providers`() = runTest {
        AppProxy.update(viaServer(scope = ProxyScope.CHOSEN, providers = setOf(Providers.MUSIXMATCH)))
        proxyServer.enqueue(MockResponse().setBody("musixmatch"))

        Ktor.clientFor(Providers.MUSIXMATCH).get(target).bodyAsText() shouldBe "musixmatch"
        runCatching { Ktor.clientFor(Providers.NETEASE).get(target) }.isFailure shouldBe true
        proxyServer.requestCount shouldBe 1
    }

    @Test
    fun `proxy credentials are sent after a 407`() = runTest {
        AppProxy.update(viaServer(username = "user"))
        proxyServer.enqueue(MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate", "Basic realm=\"proxy\""))
        proxyServer.enqueue(MockResponse().setBody("authed"))

        Ktor.clientFor(Providers.QQMUSIC).get(target).bodyAsText() shouldBe "authed"

        proxyServer.takeRequest().getHeader("Proxy-Authorization") shouldBe null
        proxyServer.takeRequest().getHeader("Proxy-Authorization")!! shouldStartWith "Basic "
    }

    @Test
    fun `connection test goes through the proxy even when the scope would skip it`() {
        proxyServer.enqueue(MockResponse().setResponseCode(404))
        val settings = viaServer(scope = ProxyScope.CHOSEN)

        AppProxy.test(settings, url = target).isSuccess shouldBe true
        proxyServer.takeRequest().requestLine shouldBe "GET $target HTTP/1.1"
    }
}
