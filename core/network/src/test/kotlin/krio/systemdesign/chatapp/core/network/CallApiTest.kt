package krio.systemdesign.chatapp.core.network

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject

class CallApiTest {

    private suspend fun HttpClient.item(): JsonObject = callApi { get("item").body() }

    @Test
    fun `an error code is an IOException`() = networkTest { server, client ->
        server.enqueue(code = 500)

        assertFailure { client.item() }.isInstanceOf<IOException>()
    }

    @Test
    fun `an answer that can't be read is an IOException`() = networkTest { server, client ->
        server.enqueue(body = """{"name":""")

        assertFailure { client.item() }.isInstanceOf<IOException>()
    }

    @Test
    fun `no connection is an IOException`() = networkTest { server, client ->
        server.shutDown()

        assertFailure { client.item() }.isInstanceOf<IOException>()
    }

    // A cancelled call must stay cancelled: an IOException would let the caller carry on as after a network error.
    @Test
    fun `cancellation passes through`() = runTest {
        assertFailure {
            callApi<Unit> { throw CancellationException("Cancelled") }
        }.isInstanceOf<CancellationException>()
    }

    @Test
    fun `every request carries the auth headers, unless it sets its own`() = networkTest(
        authHeaders = AuthHeaders { mapOf("X-User-Id" to "anna") },
    ) { server, client ->
        server.enqueue(body = "{}")
        server.enqueue(body = "{}")

        client.item()
        callApi { client.get("item") { header("X-User-Id", "max") } }

        assertThat(server.takeRequest().headers["X-User-Id"]).isEqualTo("anna")
        assertThat(server.takeRequest().headers["X-User-Id"]).isEqualTo("max")
    }
}
