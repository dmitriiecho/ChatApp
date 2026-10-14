package krio.systemdesign.chatapp.server.routes

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.ConversationsPageDto
import krio.systemdesign.chatapp.contract.MessageDto
import krio.systemdesign.chatapp.server.BOT
import krio.systemdesign.chatapp.server.DM
import krio.systemdesign.chatapp.server.MAX
import krio.systemdesign.chatapp.server.read
import krio.systemdesign.chatapp.server.send
import krio.systemdesign.chatapp.server.serverTest

class ConversationRoutesTest {

    @Test
    fun `the most active chat comes first`() = serverTest {
        val page = client.get("/conversations?limit=10").body<ConversationsPageDto>()

        assertThat(page.items.map { it.id }).containsExactly(DM, "anna-bot")
        assertThat(page.hasMore).isFalse()
    }

    @Test
    fun `the next page continues below the previous one`() = serverTest {
        val first = client.get("/conversations?limit=1").body<ConversationsPageDto>()
        val second = client.get("/conversations?limit=1&beforeSortKey=${first.items.last().sortKey}")
            .body<ConversationsPageDto>()

        assertThat(first.hasMore).isTrue()
        assertThat(second.items.map { it.id }).containsExactly("anna-bot")
        assertThat(second.hasMore).isFalse()
    }

    @Test
    fun `a direct chat is titled with the other person and counts their unread messages`() = serverTest {
        val chat = client.get("/conversations/anna-bot").body<ConversationDto>()

        assertThat(chat.title).isEqualTo("Oliver")
        assertThat(chat.unreadCount).isEqualTo(1)
        assertThat(chat.lastSeq).isEqualTo(3)
        assertThat(chat.lastMessage).isNotNull().prop(MessageDto::text).isEqualTo("How are you?")
    }

    @Test
    fun `a chat the user isn't in is not found`() = serverTest(data = {
        standardData()
        chat("max-bot", listOf(MAX, BOT))
    }) {
        assertThat(client.get("/conversations/max-bot").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `a limit out of range is a bad request`() = serverTest {
        assertThat(client.get("/conversations?limit=0").status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(client.get("/conversations?limit=101").status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `a new message moves the chat to the top`() = serverTest {
        client.send("anna-bot", "Back again")

        val page = client.get("/conversations?limit=10").body<ConversationsPageDto>()
        assertThat(page.items.map { it.id }).containsExactly("anna-bot", DM)
    }

    @Test
    fun `the first sync gets no changes and the current token`() = serverTest {
        val changes = client.get("/conversations/changes?limit=10").body<ConversationChangesDto>()

        assertThat(changes.changed).isEmpty()
        assertThat(changes.resetRequired).isFalse()
        assertThat(changes.nextToken).isEqualTo("0")
    }

    @Test
    fun `a sync after a new message gets the chat as it is now`() = serverTest {
        val token = client.get("/conversations/changes?limit=10").body<ConversationChangesDto>().nextToken
        client.send(DM, "From Max", asUser = MAX)

        val changes = client.get("/conversations/changes?since=$token&limit=10").body<ConversationChangesDto>()

        assertThat(changes.changed.map { it.id }).containsExactly(DM)
        assertThat(changes.changed.single().unreadCount).isEqualTo(1)
        assertThat(changes.nextToken.toLong()).isGreaterThan(token.toLong())
        assertThat(changes.hasMore).isFalse()
    }

    @Test
    fun `a chat changed twice comes once`() = serverTest {
        val token = client.get("/conversations/changes?limit=10").body<ConversationChangesDto>().nextToken
        client.send(DM, "One", asUser = MAX)
        client.send(DM, "Two", asUser = MAX)

        val changes = client.get("/conversations/changes?since=$token&limit=10").body<ConversationChangesDto>()

        assertThat(changes.changed.map { it.id }).containsExactly(DM)
        assertThat(changes.changed.single().lastSeq).isEqualTo(4)
    }

    @Test
    fun `many changes come in batches`() = serverTest {
        val token = client.get("/conversations/changes?limit=10").body<ConversationChangesDto>().nextToken
        client.send(DM, "One")
        client.send("anna-bot", "Two")

        val first = client.get("/conversations/changes?since=$token&limit=1").body<ConversationChangesDto>()
        val second = client.get(
            "/conversations/changes?since=${first.nextToken}&limit=1",
        ).body<ConversationChangesDto>()

        assertThat(first.changed.map { it.id }).containsExactly(DM)
        assertThat(first.hasMore).isTrue()
        assertThat(second.changed.map { it.id }).containsExactly("anna-bot")
        assertThat(second.hasMore).isFalse()
    }

    @Test
    fun `reading on another device is a change too`() = serverTest {
        val token = client.get("/conversations/changes?limit=10").body<ConversationChangesDto>().nextToken
        client.read("anna-bot", seq = 3)

        val changes = client.get("/conversations/changes?since=$token&limit=10").body<ConversationChangesDto>()

        assertThat(changes.changed.single().unreadCount).isEqualTo(0)
    }

    @Test
    fun `a token whose changes were dropped needs a reset`() = serverTest(journalLimit = 2) {
        val token = client.get("/conversations/changes?limit=10").body<ConversationChangesDto>().nextToken
        repeat(3) { client.send(DM, "Message $it", clientId = "c$it", asUser = MAX) }

        val changes = client.get("/conversations/changes?since=$token&limit=10").body<ConversationChangesDto>()

        assertThat(changes.resetRequired).isTrue()
    }

    @Test
    fun `a broken token or one from another database needs a reset`() = serverTest {
        val broken = client.get("/conversations/changes?since=abc&limit=10").body<ConversationChangesDto>()
        val ahead = client.get("/conversations/changes?since=999&limit=10").body<ConversationChangesDto>()

        assertThat(broken.resetRequired).isTrue()
        assertThat(ahead.resetRequired).isTrue()
    }

    @Test
    fun `sorting keys are unique and only grow for each user`() = serverTest {
        client.send("anna-bot", "One")
        client.send(DM, "Two", asUser = MAX)

        val keys = client.get("/conversations?limit=10").body<ConversationsPageDto>().items.map { it.sortKey }
        assertThat(keys).isEqualTo(keys.distinct().sortedDescending())
        assertThat(keys.first()).isEqualTo(4)
    }
}
