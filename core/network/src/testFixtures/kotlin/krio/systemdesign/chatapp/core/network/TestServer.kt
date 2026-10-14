package krio.systemdesign.chatapp.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.io.IOException

// Stands in for the server in networkTest: answers requests with the queued responses and records the requests.
class TestServer {

    // A request as the server got it. target is the path with the query: "/conversations?limit=50".
    data class Request(
        val method: String,
        val target: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private class Response(
        val code: Int,
        val body: String,
    )

    private val responses = ArrayDeque<Response>()
    private val requests = ArrayDeque<Request>()
    private var isDown = false

    val engine = MockEngine { request ->
        if (isDown) throw IOException("Server is down")
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        val headers = request.headers.entries().associate { (name, values) -> name to values.joinToString(", ") }
        requests.addLast(Request(request.method.value, request.url.encodedPathAndQuery, headers, body))
        val response = checkNotNull(responses.removeFirstOrNull()) { "No response queued for ${request.url}" }
        respond(
            content = response.body,
            status = HttpStatusCode.fromValue(response.code),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    // Queues the answer to the next request.
    fun enqueue(
        code: Int = 200,
        body: String = "",
    ) {
        responses.addLast(Response(code, body))
    }

    // The oldest request not taken yet.
    fun takeRequest(): Request = checkNotNull(requests.removeFirstOrNull()) { "No request was sent" }

    val requestCount: Int get() = requests.size

    // Every later request fails as with no connection.
    fun shutDown() {
        isDown = true
    }

    // Requests work again.
    fun restart() {
        isDown = false
    }

    companion object {
        const val BASE_URL = "http://localhost/"
    }
}
