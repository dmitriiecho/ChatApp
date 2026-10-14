package krio.systemdesign.chatapp.core.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// How the app reads and writes the server's JSON. The server has the same settings (ServerJson.kt in server/).
val networkJson: Json = Json {
    ignoreUnknownKeys = true
    // The server leaves null fields out.
    explicitNulls = false
    encodeDefaults = true
}

// The client settings the server's API relies on. Public so that tests set up their client the same way.
fun HttpClientConfig<*>.serverApi(
    baseUrl: String,
    authHeaders: AuthHeaders,
) {
    install(ContentNegotiation) { json(networkJson) }
    install(WebSockets)
    // A 4xx or 5xx answer throws ResponseException, which callApi turns into IOException.
    expectSuccess = true
    defaultRequest {
        url(baseUrl)
        // Only headers the request doesn't set itself: defaultRequest appends to a request's own header, and the
        // server would get both values as one.
        authHeaders.headers().forEach { (name, value) -> if (!headers.contains(name)) header(name, value) }
    }
}
