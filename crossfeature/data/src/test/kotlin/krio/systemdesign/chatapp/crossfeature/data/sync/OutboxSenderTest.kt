package krio.systemdesign.chatapp.crossfeature.data.sync

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.contract.SendMessageResponseDto
import krio.systemdesign.chatapp.core.network.apiRequest
import krio.systemdesign.chatapp.core.network.apiSampleText
import krio.systemdesign.chatapp.core.network.networkJson
import krio.systemdesign.chatapp.crossfeature.data.DataTestScope
import krio.systemdesign.chatapp.crossfeature.data.conversationDto
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import krio.systemdesign.chatapp.crossfeature.data.messageDto
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OutboxSenderTest {

    private suspend fun DataTestScope.queued(conversationId: String) = outbox.observe(conversationId).first()

    private suspend fun DataTestScope.cachedSeqs(conversationId: String) =
        db.messageDao().observeWindow(conversationId, 1, null).first().map { it.seq }

    @Test
    fun `a written message waits in the outbox and the sending is scheduled`() = dataTest {
        outbox.enqueue("anna-bot", "  Fine, thanks  ")

        assertThat(queued("anna-bot").map { it.text }).containsExactly("Fine, thanks")
        assertThat(scheduler.scheduled).isEqualTo(1)
    }

    @Test
    fun `a sent message moves from the outbox into the chat`() = dataTest {
        cacheMessages("anna-bot", 1L..3L)
        outbox.enqueue("anna-bot", "Fine, thanks")
        server.enqueue(body = apiSampleText("send-message-response.json"))

        outboxSender.drain()

        val request = server.takeRequest()
        assertThat("${request.method} ${request.target}").isEqualTo(apiRequest("send-message"))
        val body = networkJson.parseToJsonElement(request.body).jsonObject
        assertThat(body.getValue("text").jsonPrimitive.content).isEqualTo("Fine, thanks")
        assertThat(body.getValue("clientId").jsonPrimitive.content).isEqualTo(queuedClientIdWas(request.body))
        assertThat(queued("anna-bot")).isEmpty()
        assertThat(cachedSeqs("anna-bot")).containsExactly(4L, 3L, 2L, 1L)
        assertThat(db.conversationDao().lastSeq("anna-bot")).isEqualTo(4)
    }

    @Test
    fun `without the network the messages show as not sent`() = dataTest {
        outbox.enqueue("anna-bot", "One")
        server.shutDown()

        assertFailure { outboxSender.drain() }.isInstanceOf<IOException>()
        assertThat(queued("anna-bot").single().isFailed).isTrue()
    }

    @Test
    fun `a message the server refuses for good doesn't block the queue`() = dataTest {
        outbox.enqueue("gone", "Lost")
        server.enqueue(code = 404)

        outboxSender.drain()

        assertThat(queued("gone")).isEmpty()
    }

    @Test
    fun `missed messages are loaded first, so the sent one joins the cache`() = dataTest {
        cacheMessages("c", 1L..2L)
        outbox.enqueue("c", "Mine")
        respond(
            SendMessageResponseDto(
                messageDto("c", 4, authorId = "anna", text = "Mine"),
                conversationDto("c", 5, lastSeq = 4),
            ),
        )
        respond(MessagesPageDto(listOf(messageDto("c", 4, authorId = "anna", text = "Mine"), messageDto("c", 3))))

        outboxSender.drain()

        server.takeRequest()
        assertThat(server.takeRequest().target).isEqualTo("/conversations/c/messages?limit=50&afterSeq=2")
        assertThat(cachedSeqs("c")).containsExactly(4L, 3L, 2L, 1L)
    }

    // The clientId the sender used is the outbox row's: read back from the request it sent.
    private fun queuedClientIdWas(body: String) = networkJson.parseToJsonElement(body).jsonObject.getValue("clientId")
        .jsonPrimitive.content
}
