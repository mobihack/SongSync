package pl.lambada.songsync.testutil

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * A fake HTTP backend for provider tests: [handler] answers each request,
 * and every request is recorded in [requests] for assertions.
 */
class FakeHttp(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) {
    private val engine = MockEngine { request -> handler(request) }
    val client = HttpClient(engine)
    val requests: List<HttpRequestData> get() = engine.requestHistory
}

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

fun MockRequestHandleScope.respondText(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "text/plain"))

fun MockRequestHandleScope.notFound(): HttpResponseData =
    respond("", HttpStatusCode.NotFound)

/** Text body of a JSON/text request. */
val HttpRequestData.textBody: String
    get() = (body as? TextContent)?.text.orEmpty()

/** Form fields of a form-encoded request. */
val HttpRequestData.formFields: Map<String, String?>
    get() = (body as? FormDataContent)?.formData?.names()
        ?.associateWith { (body as FormDataContent).formData[it] }
        .orEmpty()

/** Last path segment of the request, e.g. "macro.subtitles.get" or "search". */
val HttpRequestData.action: String
    get() = url.encodedPath.substringAfterLast('/')
