package krio.systemdesign.chatapp.server.routes

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import krio.systemdesign.chatapp.contract.ApiHeaders
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.server.DM
import krio.systemdesign.chatapp.server.MAX
import krio.systemdesign.chatapp.server.read
import krio.systemdesign.chatapp.server.send
import krio.systemdesign.chatapp.server.serverJson
import krio.systemdesign.chatapp.server.serverTest

class EventRoutesTest {

    private suspend fun DefaultClientWebSocketSession.nextEvent(): RealtimeEventDto {
        val frame = incoming.receive() as Frame.Text
        return serverJson.decodeFromString(RealtimeEventDto.serializer(), frame.readText())
    }

    @Test
    fun `a message from someone else comes as the message and the chat`() = serverTest {
        client.webSocket("/events") {
            client.send(DM, "Are you there?", asUser = MAX)

            val message = nextEvent() as RealtimeEventDto.MessageNew
            val chat = nextEvent() as RealtimeEventDto.ConversationUpdated
            assertThat(message.message.text).isEqualTo("Are you there?")
            assertThat(chat.conversation.unreadCount).isEqualTo(1)
        }
    }

    @Test
    fun `a read on one device reaches the others`() = serverTest {
        client.webSocket("/events") {
            client.read("anna-bot", seq = 3)

            val chat = nextEvent() as RealtimeEventDto.ConversationUpdated
            assertThat(chat.conversation.unreadCount).isEqualTo(0)
        }
    }

    @Test
    fun `an edit of the last message changes the chat too`() = serverTest {
        client.webSocket("/events") {
            chat.edit("anna-bot:3", "How do you do?")

            assertThat(nextEvent()).isInstanceOf<RealtimeEventDto.MessageChanged>()
            val chat = nextEvent() as RealtimeEventDto.ConversationUpdated
            assertThat(chat.conversation.lastMessage?.text).isEqualTo("How do you do?")
        }
    }

    @Test
    fun `a socket with a wrong key is refused`() = serverTest {
        assertFailure {
            client.webSocket("/events", request = { header(ApiHeaders.API_KEY, "wrong") }) {}
        }
    }
}
