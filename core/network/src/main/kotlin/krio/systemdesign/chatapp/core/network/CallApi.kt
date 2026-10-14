package krio.systemdesign.chatapp.core.network

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

// A call to the server. Whatever goes wrong comes out as IOException, which to a screen means "no connection":
// no network or a timeout already is one; an error code (ResponseException) and a response that can't be read
// are turned into one. Callers above the data layer then never see Ktor's exceptions. Cancellation passes through.
suspend inline fun <T> callApi(block: suspend () -> T): T = try {
    block()
} catch (e: ResponseException) {
    throw IOException("The server answered ${e.response.status}", e)
} catch (e: ContentConvertException) {
    throw IOException("The server's answer can't be read", e)
} catch (e: NoTransformationFoundException) {
    throw IOException("The server's answer can't be read", e)
} catch (e: SerializationException) {
    throw IOException("The server's answer can't be read", e)
}
