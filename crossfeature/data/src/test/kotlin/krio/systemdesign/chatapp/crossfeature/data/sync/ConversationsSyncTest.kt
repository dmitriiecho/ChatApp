package krio.systemdesign.chatapp.crossfeature.data.sync

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.contract.ConversationsPageDto
import krio.systemdesign.chatapp.core.network.TestServer
import krio.systemdesign.chatapp.core.network.apiRequest
import krio.systemdesign.chatapp.core.network.apiSampleText
import krio.systemdesign.chatapp.crossfeature.data.DataTestScope
import krio.systemdesign.chatapp.crossfeature.data.conversationDto
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationsSyncTest {

    private fun TestServer.Request.line() = "$method $target"

    private suspend fun DataTestScope.firstSync() {
        server.enqueue(body = apiSampleText("conversation-changes-start.json"))
        server.enqueue(body = apiSampleText("conversations-page.json"))
        conversationsSync.sync()
    }

    private suspend fun DataTestScope.listIds() = conversations.observeConversations(0, 10).first().items.map { it.id }

    @Test
    fun `the first sync takes the token, then the first page`() = dataTest {
        firstSync()

        assertThat(server.takeRequest().line()).isEqualTo(apiRequest("conversation-changes-start"))
        assertThat(server.takeRequest().line()).isEqualTo(apiRequest("conversations-page"))
        assertThat(listIds()).containsExactly("dm-anna-max", "anna-bot")
        assertThat(db.conversationListMetaDao().get()?.syncToken).isEqualTo("0")
    }

    @Test
    fun `the list is loading until its first sync`() = dataTest {
        assertThat(conversations.observeConversations(0, 10).first().isSynced).isFalse()

        firstSync()

        assertThat(conversations.observeConversations(0, 10).first().isSynced).isTrue()
    }

    @Test
    fun `a later sync applies the changes after the token and keeps the next one`() = dataTest {
        firstSync()
        repeat(2) { server.takeRequest() }
        server.enqueue(body = apiSampleText("conversation-changes.json"))

        conversationsSync.sync()

        assertThat(server.takeRequest().line()).isEqualTo(apiRequest("conversation-changes"))
        val chat = conversations.observeConversations(0, 10).first().items.first()
        assertThat(chat.id).isEqualTo("dm-anna-max")
        assertThat(chat.unreadCount).isEqualTo(1)
        assertThat(db.conversationListMetaDao().get()?.syncToken).isEqualTo("1")
    }

    @Test
    fun `changes come batch after batch`() = dataTest {
        firstSync()
        respond(
            ConversationChangesDto(
                listOf(conversationDto("anna-bot", sortKey = 10, version = 2)),
                emptyList(),
                "5",
                true,
                false,
            ),
        )
        respond(
            ConversationChangesDto(
                listOf(conversationDto("dm-anna-max", sortKey = 11, version = 2)),
                emptyList(),
                "6",
                false,
                false,
            ),
        )

        conversationsSync.sync()

        assertThat(listIds()).containsExactly("dm-anna-max", "anna-bot")
        assertThat(db.conversationListMetaDao().get()?.syncToken).isEqualTo("6")
    }

    @Test
    fun `a removed chat leaves the list`() = dataTest {
        firstSync()
        respond(ConversationChangesDto(emptyList(), listOf("anna-bot"), "2", false, false))

        conversationsSync.sync()

        assertThat(listIds()).containsExactly("dm-anna-max")
    }

    @Test
    fun `a token too old starts the list over`() = dataTest {
        firstSync()
        respond(ConversationChangesDto(emptyList(), emptyList(), "9", hasMore = false, resetRequired = true))
        respond(ConversationChangesDto(emptyList(), emptyList(), "9", hasMore = false, resetRequired = false))
        respond(ConversationsPageDto(listOf(conversationDto("fresh", sortKey = 1)), hasMore = false))

        conversationsSync.sync()

        assertThat(listIds()).containsExactly("fresh")
        assertThat(db.conversationListMetaDao().get()?.syncToken).isEqualTo("9")
    }

    @Test
    fun `a failed sync keeps the old token`() = dataTest {
        firstSync()
        server.shutDown()

        assertFailure { conversationsSync.sync() }.isInstanceOf<IOException>()
        assertThat(db.conversationListMetaDao().get()?.syncToken).isEqualTo("0")
    }
}
