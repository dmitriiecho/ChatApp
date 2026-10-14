package krio.systemdesign.chatapp.server.routes

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.MessageChangesDto
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.contract.SendMessageResponseDto
import krio.systemdesign.chatapp.server.BOT
import krio.systemdesign.chatapp.server.MAX
import krio.systemdesign.chatapp.server.NOW
import krio.systemdesign.chatapp.server.read
import krio.systemdesign.chatapp.server.send
import krio.systemdesign.chatapp.server.serverTest

class MessageRoutesTest {

    private suspend fun io.ktor.client.HttpClient.seqs(query: String): List<Long> =
        get("/conversations/anna-bot/messages?$query").body<MessagesPageDto>().items.map { it.seq }

    @Test
    fun `pages come newest first`() = serverTest {
        assertThat(client.seqs("limit=2")).containsExactly(3L, 2L)
        assertThat(client.seqs("limit=10&beforeSeq=3")).containsExactly(2L, 1L)
        assertThat(client.seqs("limit=10&afterSeq=1")).containsExactly(3L, 2L)
    }

    @Test
    fun `a page around a message holds it and both sides`() = serverTest {
        assertThat(client.seqs("limit=3&aroundSeq=2")).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun `two anchors at once are a bad request`() = serverTest {
        val response = client.get("/conversations/anna-bot/messages?limit=10&beforeSeq=3&afterSeq=1")

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `messages of a chat the user isn't in are not found`() = serverTest(data = {
        standardData()
        chat("max-bot", listOf(MAX, BOT))
    }) {
        assertThat(client.get("/conversations/max-bot/messages?limit=10").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `a sent message gets the next seq and the chat is read up to it`() = serverTest {
        val response = client.send("anna-bot", "  Fine, thanks  ").body<SendMessageResponseDto>()

        assertThat(response.message.seq).isEqualTo(4)
        assertThat(response.message.text).isEqualTo("Fine, thanks")
        assertThat(response.message.sentAtMillis).isEqualTo(NOW)
        assertThat(response.conversation.lastSeq).isEqualTo(4)
        assertThat(response.conversation.lastReadSeq).isEqualTo(4)
        assertThat(response.conversation.unreadCount).isEqualTo(0)
    }

    @Test
    fun `a retry with the same client id doesn't send twice`() = serverTest {
        val first = client.send("anna-bot", "Once", clientId = "c1").body<SendMessageResponseDto>()
        val retry = client.send("anna-bot", "Once", clientId = "c1").body<SendMessageResponseDto>()

        assertThat(retry.message).isEqualTo(first.message)
        assertThat(client.seqs("limit=10").first()).isEqualTo(4)
    }

    @Test
    fun `an empty or too long message is a bad request`() = serverTest {
        assertThat(client.send("anna-bot", "   ").status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(client.send("anna-bot", "a".repeat(MAX_TEXT_LENGTH + 1), clientId = "long").status)
            .isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `too many messages in a minute are refused`() = serverTest {
        repeat(RateLimits.SENDS_PER_MINUTE) { client.send("anna-bot", "Message $it", clientId = "c$it") }

        val response = client.send("anna-bot", "One more", clientId = "extra")

        assertThat(response.status).isEqualTo(HttpStatusCode.TooManyRequests)
    }

    @Test
    fun `reading moves the mark up, never down or past the last message`() = serverTest {
        val read = client.read("anna-bot", seq = 3).body<ConversationDto>()
        val back = client.read("anna-bot", seq = 1).body<ConversationDto>()
        val past = client.read("anna-bot", seq = 99).body<ConversationDto>()

        assertThat(read.unreadCount).isEqualTo(0)
        assertThat(back.lastReadSeq).isEqualTo(3)
        assertThat(past.lastReadSeq).isEqualTo(3)
        assertThat(back.version).isEqualTo(read.version)
    }

    @Test
    fun `edits and deletions come in the chat's sync`() = serverTest {
        val token = client.get("/conversations/anna-bot/messages/changes?limit=10").body<MessageChangesDto>().nextToken
        chat.edit("anna-bot:1", "Hey!")
        chat.delete("anna-bot:2")

        val changes = client.get(
            "/conversations/anna-bot/messages/changes?since=$token&limit=10",
        ).body<MessageChangesDto>()

        val (edited, deleted) = changes.changed
        assertThat(edited.text).isEqualTo("Hey!")
        assertThat(edited.version).isEqualTo(2)
        assertThat(edited.editedAtMillis).isEqualTo(NOW)
        assertThat(deleted.isDeleted).isTrue()
        assertThat(deleted.text).isEqualTo("")
        assertThat(changes.hasMore).isFalse()
    }

    @Test
    fun `a sync of a chat with no edits gets nothing`() = serverTest {
        val token = client.get("/conversations/anna-bot/messages/changes?limit=10").body<MessageChangesDto>().nextToken
        client.send("anna-bot", "New, not an edit")

        val changes = client.get(
            "/conversations/anna-bot/messages/changes?since=$token&limit=10",
        ).body<MessageChangesDto>()

        assertThat(changes.changed).isEmpty()
        assertThat(changes.nextToken).isEqualTo(token)
    }

    @Test
    fun `a chat's token whose edits were dropped needs a reset`() = serverTest(journalLimit = 1) {
        val token = client.get("/conversations/anna-bot/messages/changes?limit=10").body<MessageChangesDto>().nextToken
        chat.edit("anna-bot:1", "One")
        chat.edit("anna-bot:1", "Two")

        val changes = client.get(
            "/conversations/anna-bot/messages/changes?since=$token&limit=10",
        ).body<MessageChangesDto>()

        assertThat(changes.resetRequired).isTrue()
    }
}
