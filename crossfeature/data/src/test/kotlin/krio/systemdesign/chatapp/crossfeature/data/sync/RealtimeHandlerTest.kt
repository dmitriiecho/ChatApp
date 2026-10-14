package krio.systemdesign.chatapp.crossfeature.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.single
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import krio.systemdesign.chatapp.contract.BotsDto
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.contract.ConversationsPageDto
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.core.network.apiSampleText
import krio.systemdesign.chatapp.core.network.networkJson
import krio.systemdesign.chatapp.crossfeature.data.DataTestScope
import krio.systemdesign.chatapp.crossfeature.data.conversationDto
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import krio.systemdesign.chatapp.crossfeature.data.local.ConversationListMetaEntity
import krio.systemdesign.chatapp.crossfeature.data.messageDto
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealtimeHandlerTest {

    private suspend fun DataTestScope.everythingLoaded() = db.conversationListMetaDao()
        .upsert(ConversationListMetaEntity(loadedDownToSortKey = Long.MIN_VALUE, hasMore = false, syncToken = "0"))

    private suspend fun DataTestScope.cachedSeqs(conversationId: String) =
        db.messageDao().observeWindow(conversationId, 1, null).first().map { it.seq }

    @Test
    fun `a new message continuing the cache is added`() = dataTest {
        cacheMessages("c", 1L..2L)

        realtime.onEvent(RealtimeEventDto.MessageNew(messageDto("c", 3)))

        assertThat(cachedSeqs("c")).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun `a new message after a gap or in a chat not cached is left for the chat to load`() = dataTest {
        cacheMessages("c", 1L..2L)

        realtime.onEvent(RealtimeEventDto.MessageNew(messageDto("c", 5)))
        realtime.onEvent(RealtimeEventDto.MessageNew(messageDto("other", 1)))

        assertThat(db.messageDao().maxSeq("c")).isEqualTo(2)
        assertThat(db.messageDao().maxSeq("other")).isEqualTo(null)
    }

    @Test
    fun `an event older than the cached chat changes nothing`() = dataTest {
        everythingLoaded()
        db.conversationDao().upsertNewer(listOf(conversationDto("c", sortKey = 1, version = 6).toEntity()))

        realtime.onEvent(
            RealtimeEventDto.ConversationUpdated(conversationDto("c", sortKey = 1, version = 5, unreadCount = 3)),
        )

        assertThat(db.conversationDao().version("c")).isEqualTo(6)
    }

    @Test
    fun `a push is applied and its events come back for a notification`() = dataTest {
        everythingLoaded()
        val data = networkJson.decodeFromString<Map<String, String>>(apiSampleText("push-data.json"))

        val events = realtime.onPush(data)

        assertThat(events).single().isInstanceOf<RealtimeEventDto.ConversationUpdated>()
        assertThat(conversations.observeConversations(0, 10).first().items.single().unreadCount).isEqualTo(1)
    }

    @Test
    fun `a push that came late shows no notification`() = dataTest {
        everythingLoaded()
        val data = networkJson.decodeFromString<Map<String, String>>(apiSampleText("push-data.json"))
        // Read on another device meanwhile: a newer version is cached.
        db.conversationDao().upsertNewer(listOf(conversationDto("dm-anna-max", sortKey = 3, version = 3).toEntity()))

        assertThat(realtime.onPush(data)).isEmpty()
    }

    @Test
    fun `an event type this app doesn't know is skipped`() = dataTest {
        val push = mapOf("events" to """[{"type":"typing.started","conversationId":"c"}]""")

        assertThat(realtime.onPush(push)).isEmpty()
    }

    @Test
    fun `a connection syncs the list, sends the outbox and reads the bots switch`() = dataTest {
        respond(ConversationChangesDto(emptyList(), emptyList(), "0", false, false))
        respond(ConversationsPageDto(listOf(conversationDto("c", 1)), hasMore = false))
        respond(BotsDto(enabled = false))

        realtime.onReconnected()

        assertThat(conversations.observeConversations(0, 10).first().items.map { it.id }).containsExactly("c")
        assertThat(scheduler.scheduled).isEqualTo(1)
        assertThat(bots.isEnabled.value).isEqualTo(false)
    }

    @Test
    fun `a server without the bots switch still connects`() = dataTest {
        respond(ConversationChangesDto(emptyList(), emptyList(), "0", false, false))
        respond(ConversationsPageDto(emptyList(), hasMore = false))
        server.enqueue(code = 404)

        realtime.onReconnected()

        assertThat(bots.isEnabled.value).isEqualTo(null)
    }

    @Test
    fun `a bots event changes the switch`() = dataTest {
        realtime.onEvent(RealtimeEventDto.BotsChanged(enabled = false))

        assertThat(bots.isEnabled.value).isEqualTo(false)
    }
}
