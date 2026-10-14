package krio.systemdesign.chatapp.crossfeature.data.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.core.network.TestServer
import krio.systemdesign.chatapp.core.network.apiRequest
import krio.systemdesign.chatapp.core.network.apiSample
import krio.systemdesign.chatapp.core.network.apiSampleText
import krio.systemdesign.chatapp.core.network.networkJson
import krio.systemdesign.chatapp.crossfeature.data.DataTestScope
import krio.systemdesign.chatapp.crossfeature.data.conversationDto
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import krio.systemdesign.chatapp.crossfeature.data.messageDto
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessagesRepositoryImplTest {

    private fun TestServer.Request.line() = "$method $target"

    private suspend fun DataTestScope.chatWithLastSeq(lastSeq: Long) =
        db.conversationDao().upsertNewer(listOf(conversationDto("c", sortKey = 1, lastSeq = lastSeq).toEntity()))

    @Test
    fun `nothing cached opens no window`() = dataTest {
        assertThat(messages.openLatest("anna-bot")).isNull()
    }

    @Test
    fun `a refresh loads the chat, its edits, then its newest page`() = dataTest {
        server.enqueue(body = apiSampleText("conversation.json"))
        server.enqueue(body = apiSampleText("message-changes-start.json"))
        server.enqueue(body = apiSampleText("messages-page.json"))

        messages.refreshLatest("anna-bot")

        assertThat(server.takeRequest().line()).isEqualTo(apiRequest("conversation"))
        assertThat(server.takeRequest().line()).isEqualTo(apiRequest("message-changes-start"))
        assertThat(server.takeRequest().line()).isEqualTo(apiRequest("messages-page"))
        val window = messages.openLatest("anna-bot")!!
        assertThat(window).isEqualTo(Window(fromSeq = 1, toSeq = null))
        val shown = messages.observe("anna-bot", window).first()
        assertThat(shown.map { it.seq }).containsExactly(3L, 2L, 1L)
        // Anna wrote the second one.
        assertThat(shown.map { it.isMine }).containsExactly(false, true, false)
    }

    @Test
    fun `older messages in the cache need no request`() = dataTest {
        chatWithLastSeq(100)
        cacheMessages("c", 1L..100L)

        assertThat(messages.loadOlder("c", beforeSeq = 60)).isEqualTo(10)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `older messages missing from the cache come from the server`() = dataTest {
        chatWithLastSeq(100)
        cacheMessages("c", 51L..100L)
        respond(MessagesPageDto((50L downTo 1L).map { messageDto("c", it) }))

        val fromSeq = messages.loadOlder("c", beforeSeq = 51)

        assertThat(server.takeRequest().target).isEqualTo("/conversations/c/messages?limit=50&beforeSeq=51")
        assertThat(fromSeq).isEqualTo(1)
    }

    @Test
    fun `offline the window grows by what the cache has, and fails with nothing to show`() = dataTest {
        chatWithLastSeq(100)
        cacheMessages("c", 40L..100L)
        server.shutDown()

        assertThat(messages.loadOlder("c", beforeSeq = 60)).isEqualTo(40)
        assertFailure { messages.loadOlder("c", beforeSeq = 40) }.isInstanceOf<IOException>()
    }

    @Test
    fun `newer messages up to the end of the chat open the window again`() = dataTest {
        chatWithLastSeq(30)
        cacheMessages("c", 1L..30L)

        assertThat(messages.loadNewer("c", afterSeq = 10)).isNull()
    }

    @Test
    fun `a window around a message not cached loads the page around it`() = dataTest {
        chatWithLastSeq(1_000)
        respond(MessagesPageDto((525L downTo 476L).map { messageDto("c", it) }))

        val window = messages.openAround("c", seq = 500)

        assertThat(server.takeRequest().target).isEqualTo("/conversations/c/messages?limit=50&aroundSeq=500")
        assertThat(window).isEqualTo(Window(fromSeq = 476, toSeq = 525))
    }

    @Test
    fun `a message the server doesn't have opens no window`() = dataTest {
        chatWithLastSeq(10)
        respond(MessagesPageDto(emptyList()))

        assertThat(messages.openAround("c", seq = 500)).isNull()
    }

    @Test
    fun `reading sends the seq and caches the chat as the server returns it`() = dataTest {
        server.enqueue(body = apiSampleText("conversation-read.json"))

        messages.markRead("anna-bot", seq = 3)

        val request = server.takeRequest()
        assertThat(request.line()).isEqualTo(apiRequest("read"))
        assertThat(networkJson.parseToJsonElement(request.body)).isEqualTo(apiSample("read-request.json"))
        assertThat(messages.observeConversation("anna-bot").first()?.unreadCount).isEqualTo(0)
    }
}
