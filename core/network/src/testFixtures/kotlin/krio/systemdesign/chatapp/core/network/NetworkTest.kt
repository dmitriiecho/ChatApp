package krio.systemdesign.chatapp.core.network

import io.ktor.client.HttpClient
import java.io.File
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Runs a test against a TestServer, with a client that has the app's settings (serverApi) and the given headers.
fun networkTest(
    authHeaders: AuthHeaders = AuthHeaders { emptyMap() },
    block: suspend TestScope.(server: TestServer, client: HttpClient) -> Unit,
): TestResult = runTest {
    val server = TestServer()
    HttpClient(server.engine) { serverApi(TestServer.BASE_URL, authHeaders) }.use { client ->
        block(server, client)
    }
}

// A sample from server/api-samples as text: the JSON the app and the server agree on. The server's tests read the
// same files and check it answers exactly so.
fun apiSampleText(name: String): String = File(API_SAMPLES_DIR, name).readText()

fun apiSample(name: String): JsonElement = networkJson.parseToJsonElement(apiSampleText(name))

// A request from server/api-samples/requests.json, as the method and the target: "GET /users".
// The server's tests send the same requests.
fun apiRequest(name: String): String = apiSample("requests.json").jsonObject.getValue(name).jsonPrimitive.content
