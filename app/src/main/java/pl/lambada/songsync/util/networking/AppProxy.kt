package pl.lambada.songsync.util.networking

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.lambada.songsync.util.Providers
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

enum class ProxyMode {
    /** Direct connections, as before proxies existed. */
    OFF,

    /** Android's Wi-Fi or global proxy. */
    SYSTEM,

    /** The host and port in [ProxySettings]. */
    MANUAL,
}

enum class ProxyType { HTTP, SOCKS }

/** What goes through the proxy. */
enum class ProxyScope {
    /** Every request the app makes. */
    ALL,

    /** Only requests made for [ProxySettings.providers]; everything else connects directly. */
    CHOSEN,
}

data class ProxySettings(
    val mode: ProxyMode = ProxyMode.OFF,
    val type: ProxyType = ProxyType.SOCKS,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    /** HTTP proxies only; OkHttp has no hook for SOCKS authentication. */
    val username: String = "",
    val password: String = "",
    val scope: ProxyScope = ProxyScope.ALL,
    /** Kept when [scope] is [ProxyScope.ALL], so switching back restores the choice. */
    val providers: Set<Providers> = emptySet(),
) {
    val isManualValid: Boolean get() = isValidHost(host) && isValidPort(port)

    val usesCredentials: Boolean get() = type == ProxyType.HTTP && username.isNotBlank()

    /** Whether requests for [provider] (or, when null, the app's other requests) use the proxy. */
    fun appliesTo(provider: Providers?): Boolean = when {
        mode == ProxyMode.OFF -> false
        scope == ProxyScope.ALL -> true
        else -> provider != null && provider in providers
    }

    /** The proxy for [ProxyMode.MANUAL], or null when the host or port is invalid. */
    fun manualProxy(): Proxy? {
        if (!isManualValid) return null
        val host = host.trim()
        return when (type) {
            // OkHttp resolves an HTTP proxy's host itself
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
            // The JDK SOCKS socket expects a resolved proxy address. The target host stays
            // unresolved, so the proxy does the lookup (which also gets round DNS blocks).
            ProxyType.SOCKS -> Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
        }
    }

    companion object {
        const val DEFAULT_PORT = 1080

        fun isValidPort(port: Int) = port in 1..65535

        fun isValidHost(host: String): Boolean {
            val h = host.trim()
            return h.isNotEmpty() && h.none { it.isWhitespace() || it == '/' || it == '@' }
        }
    }
}

/**
 * The proxy every HTTP client in the app follows. Clients read [settings] on each new connection,
 * so a change applies to the next request without rebuilding anything.
 *
 * Set by [pl.lambada.songsync.data.UserSettingsController], which every entry point (activities and
 * the Poweramp receiver) creates before making a request.
 */
object AppProxy {
    @Volatile
    var settings: ProxySettings = ProxySettings()
        private set

    /**
     * Where requests go when the manual proxy is invalid: a closed local port. Requests fail
     * instead of quietly bypassing the proxy the user asked for.
     */
    internal val unreachable = Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLoopbackAddress(), 1))

    /** Applies [to]; [pool] is cleared so keep-alive connections made the old way aren't reused. */
    fun update(to: ProxySettings, pool: ConnectionPool? = Ktor.connectionPool) {
        if (to == settings) return
        settings = to
        pool?.evictAll()
    }

    /** For [provider]'s requests, or the app's other requests when null. */
    fun selectorFor(provider: Providers?): ProxySelector = Selector { s -> s.takeIf { it.appliesTo(provider) } }

    /** Uses [settings]' proxy for every request, whatever its scope. For [test]. */
    private fun selectorIgnoringScope(settings: ProxySettings): ProxySelector = Selector(current = { settings }) { it }

    private class Selector(
        private val current: () -> ProxySettings = { settings },
        /** [current] settings if they apply to this client's requests, else null. */
        private val applicable: (ProxySettings) -> ProxySettings?,
    ) : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val s = applicable(current()) ?: return listOf(Proxy.NO_PROXY)
            return when (s.mode) {
                ProxyMode.OFF -> listOf(Proxy.NO_PROXY)
                ProxyMode.SYSTEM -> systemProxies(uri)
                ProxyMode.MANUAL -> listOf(s.manualProxy() ?: unreachable)
            }
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    private fun systemProxies(uri: URI?): List<Proxy> {
        val system = ProxySelector.getDefault() ?: return listOf(Proxy.NO_PROXY)
        return runCatching { system.select(uri) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
    }

    /** Answers a 407 with Basic credentials from [settings], once. */
    fun authenticator(current: () -> ProxySettings = { settings }): Authenticator = Authenticator { _, response ->
        val s = current()
        when {
            s.mode != ProxyMode.MANUAL || !s.usesCredentials -> null
            // Already sent and refused: give up instead of looping
            response.request.header("Proxy-Authorization") != null -> null
            else -> response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(s.username, s.password))
                .build()
        }
    }

    const val TEST_URL = "https://lrclib.net/api/search?q=songsync"

    /**
     * Makes one request through [settings]' proxy, whatever its scope.
     * @return how long it took, in milliseconds. Any HTTP response counts: it got through.
     */
    fun test(settings: ProxySettings, base: OkHttpClient = Ktor.okHttp, url: String = TEST_URL): Result<Long> = runCatching {
        val client = base.newBuilder()
            .proxySelector(selectorIgnoringScope(settings))
            .proxyAuthenticator(authenticator { settings })
            .connectionPool(ConnectionPool())
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        val start = System.nanoTime()
        client.newCall(Request.Builder().url(url.toHttpUrl()).build()).execute().use { response ->
            // A 407 that the credentials didn't fix is the proxy turning us away
            if (response.code == 407) throw IOException("407 Proxy authentication required")
        }
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }
}
