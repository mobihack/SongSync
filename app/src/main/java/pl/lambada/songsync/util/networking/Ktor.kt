package pl.lambada.songsync.util.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import pl.lambada.songsync.util.Providers
import java.util.concurrent.ConcurrentHashMap

object Ktor {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    const val CALL_TIMEOUT_SECONDS = 20L

    /** Shared by every client below, so [AppProxy.update] can drop connections made the old way. */
    val connectionPool = ConnectionPool()

    /** Base for every client: shared pool, proxy credentials from [AppProxy]. */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        // A whole request, so one blocked or blackholed provider can't stall a search for minutes
        // (OkHttp's 10 s connect and read timeouts apply per step, not per call)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .proxyAuthenticator(AppProxy.authenticator())
        .build()

    /** For requests that aren't for a lyrics provider (update check, cover art). */
    val baseOkHttp: OkHttpClient = withProxyFor(null)

    /** For requests that aren't for a lyrics provider, such as the update check. */
    val client: HttpClient = httpClient(baseOkHttp)

    private val providerClients = ConcurrentHashMap<Providers, HttpClient>()

    /**
     * The client for [provider]'s requests. One per provider, so the proxy can be limited to some
     * providers ([ProxyScope.CHOSEN]).
     */
    fun clientFor(provider: Providers): HttpClient =
        providerClients.getOrPut(provider) { httpClient(withProxyFor(provider)) }

    private fun withProxyFor(provider: Providers?): OkHttpClient =
        okHttp.newBuilder().proxySelector(AppProxy.selectorFor(provider)).build()

    fun httpClient(okHttpClient: OkHttpClient) = HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        install(ContentNegotiation) { json(json) }
    }
}
