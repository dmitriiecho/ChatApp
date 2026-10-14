package krio.systemdesign.chatapp.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlinx.serialization.json.JsonElement

// The server answers exactly as api-samples/ shows, so the app's tests, which read the same files, test against
// what the server really sends. The samples are made on the standard test data with the clock at NOW.
class ApiSamplesTest {

    private suspend fun ServerTestScope.response(
        request: String,
        body: JsonElement? = null,
    ): JsonElement = serverJson.parseToJsonElement(client.sendApiRequest(request, body).bodyAsText())

    @Test
    fun `reads match their samples`() = serverTest {
        for (name in listOf(
            "users",
            "conversations-page",
            "conversation",
            "conversation-changes-start",
            "messages-page",
            "message-changes-start",
            "bots",
        )) {
            assertThat(response(name), name).isEqualTo(apiSample("$name.json"))
        }
    }

    @Test
    fun `sending matches its sample`() = serverTest {
        assertThat(response("send-message", apiSample("send-message-request.json")))
            .isEqualTo(apiSample("send-message-response.json"))
    }

    @Test
    fun `reading matches its sample`() = serverTest {
        assertThat(response("read", apiSample("read-request.json"))).isEqualTo(apiSample("conversation-read.json"))
    }

    @Test
    fun `turning the bots off matches its sample`() = serverTest {
        assertThat(response("set-bots", apiSample("bots-request.json"))).isEqualTo(apiSample("bots-response.json"))
    }

    @Test
    fun `the chat list sync matches its sample`() = serverTest {
        client.send(DM, "Lunch at noon?", clientId = "m-1", asUser = MAX)

        assertThat(response("conversation-changes")).isEqualTo(apiSample("conversation-changes.json"))
    }

    @Test
    fun `a chat's sync matches its sample`() = serverTest {
        chat.edit("anna-bot:1", "Hi there!")
        chat.delete("anna-bot:2")

        assertThat(response("message-changes")).isEqualTo(apiSample("message-changes.json"))
    }

    @Test
    fun `events match their samples`() = serverTest {
        client.webSocket("/events") {
            suspend fun next() = serverJson.parseToJsonElement((incoming.receive() as Frame.Text).readText())

            client.send(DM, "Lunch at noon?", clientId = "m-1", asUser = MAX)
            assertThat(next()).isEqualTo(apiSample("event-message-new.json"))
            assertThat(next()).isEqualTo(apiSample("event-conversation-updated.json"))

            chat.edit("anna-bot:1", "Hi there!")
            assertThat(next()).isEqualTo(apiSample("event-message-changed.json"))

            botsSwitch.set(false)
            assertThat(next()).isEqualTo(apiSample("event-bots-changed.json"))
        }
    }
}
