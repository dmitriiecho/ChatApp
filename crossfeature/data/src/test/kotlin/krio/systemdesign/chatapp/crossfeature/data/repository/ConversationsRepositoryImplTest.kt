package krio.systemdesign.chatapp.crossfeature.data.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.contract.ConversationsPageDto
import krio.systemdesign.chatapp.crossfeature.data.DataTestScope
import krio.systemdesign.chatapp.crossfeature.data.conversationDto
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationsRepositoryImplTest {

    // A first page of two chats (sortKeys 10 and 9) with more on the server.
    private suspend fun DataTestScope.firstSync() {
        respond(ConversationChangesDto(emptyList(), emptyList(), "0", false, false))
        respond(ConversationsPageDto(listOf(conversationDto("c10", 10), conversationDto("c9", 9)), hasMore = true))
        conversationsSync.sync()
        repeat(2) { server.takeRequest() }
    }

    @Test
    fun `more on the server counts as more below`() = dataTest {
        firstSync()

        val slice = conversations.observeConversations(offset = 0, count = 2).first()

        assertThat(slice.items.size).isEqualTo(2)
        assertThat(slice.hasMore).isTrue()
    }

    @Test
    fun `loading more asks for the chats below the boundary`() = dataTest {
        firstSync()
        respond(ConversationsPageDto(listOf(conversationDto("c8", 8)), hasMore = false))

        conversations.ensureLoaded(count = 3)

        assertThat(server.takeRequest().target).isEqualTo("/conversations?beforeSortKey=9&limit=50")
        val slice = conversations.observeConversations(offset = 0, count = 10).first()
        assertThat(slice.items.map { it.id }).isEqualTo(listOf("c10", "c9", "c8"))
        assertThat(slice.hasMore).isFalse()
    }

    @Test
    fun `enough cached chats need no request`() = dataTest {
        firstSync()

        conversations.ensureLoaded(count = 2)

        assertThat(server.requestCount).isEqualTo(0)
    }
}
