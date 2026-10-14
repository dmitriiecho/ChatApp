package krio.systemdesign.chatapp.crossfeature.data.sync

import androidx.room3.withWriteTransaction
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.MessageDto
import krio.systemdesign.chatapp.contract.PushData
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.core.network.networkJson
import krio.systemdesign.chatapp.crossfeature.data.OutboxScheduler
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.removeConversations
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import krio.systemdesign.chatapp.crossfeature.data.repository.BotsRepositoryImpl
import org.koin.core.annotation.Singleton

// The way in for the server's events, from both transports: the socket while the app is on screen
// (RealtimeConnection) and pushes otherwise (onPush, for a push service in :app). Everything goes to Room, and
// open screens update by themselves.
// While connected, changes come here one by one. Events sent while offline are lost; on the next connection
// onReconnected runs both syncs, which catch up by their tokens. The same change may come several times and out of
// order (socket, push, sync): each event carries the whole object, and version keeps a late old one from
// overwriting a newer one.
// Class public, constructor internal: :app starts the connection and would hand pushes here.
@Singleton
class RealtimeHandler internal constructor(
    private val conversationsSync: ConversationsSync,
    private val messageChangesSync: MessageChangesSync,
    private val outboxScheduler: OutboxScheduler,
    private val bots: BotsRepositoryImpl,
    private val db: ChatDatabase,
) {
    private val messageDao = db.messageDao()
    private val conversationDao = db.conversationDao()

    internal suspend fun onEvent(event: RealtimeEventDto) {
        when (event) {
            is RealtimeEventDto.MessageNew -> onNewMessage(event.message)
            is RealtimeEventDto.MessageChanged -> messageDao.updateExistingNewer(listOf(event.message.toEntity()))
            is RealtimeEventDto.ConversationUpdated -> onConversationUpdated(event.conversation)
            is RealtimeEventDto.ConversationRemoved -> db.removeConversations(listOf(event.conversationId))
            is RealtimeEventDto.BotsChanged -> bots.onChanged(event.enabled)
        }
    }

    // A push: the events in its "events" field go to Room as the socket's do. Returns those that didn't come late,
    // for a push service to decide which notification to show or drop. Room is kept safe from late events by
    // version; a notification by this check: a push delayed while the phone slept may be about a chat read since.
    suspend fun onPush(data: Map<String, String>): List<RealtimeEventDto> {
        val raw = data[PushData.EVENTS] ?: return emptyList()
        val events = networkJson.parseToJsonElement(raw).jsonArray.mapNotNull { decodeEventOrNull(it) }
        events.forEach { onEvent(it) }
        return events.filterNot { isStale(it) }
    }

    // Connected, the first time included: catches up by the tokens on what was missed offline.
    //   the chat list log, whole;
    //   the edit logs of the recently opened chats (the others catch up when opened);
    //   the outbox, which waited for the network;
    //   the bots switch, which has no log: it is read again.
    // An IOException goes to RealtimeConnection, which reconnects and tries again.
    internal suspend fun onReconnected() {
        conversationsSync.sync()
        messageChangesSync.syncRecentlyOpened(limit = RECENT_CHATS_TO_SYNC)
        outboxScheduler.schedule()
        try {
            bots.refresh()
        } catch (e: IOException) {
            // The switch stays unknown: no reason to drop the socket, whose events matter more (and a server
            // older than the switch answers 404).
        }
    }

    // Cached only as a continuation of the cached history (its previous message is here). Otherwise it would be a
    // lone piece, and offline the chat would open on it; what's missing loads when the chat opens.
    private suspend fun onNewMessage(message: MessageDto) = db.withWriteTransaction {
        val cachedMaxSeq = messageDao.maxSeq(message.conversationId) ?: return@withWriteTransaction
        if (message.seq == cachedMaxSeq + 1) messageDao.upsertNewer(listOf(message.toEntity()))
    }

    // A new message raises sortKey, so the chat goes above the boundary and to the top of the list even if it
    // wasn't loaded. It also brings the chat's lastSeq to its screen.
    private suspend fun onConversationUpdated(conversation: ConversationDto) =
        conversationDao.upsertIfRelevant(listOf(conversation.toEntity()))

    // Room already has a newer version of the chat than the event.
    private suspend fun isStale(event: RealtimeEventDto): Boolean = event is RealtimeEventDto.ConversationUpdated &&
        (conversationDao.version(event.conversation.id) ?: Long.MIN_VALUE) > event.conversation.version

    private companion object {
        const val RECENT_CHATS_TO_SYNC = 10
    }
}

// An event type this version of the app doesn't know (the server added one) is null and skipped: a new type on the
// server must not break older apps.
internal fun decodeEventOrNull(element: JsonElement): RealtimeEventDto? = try {
    networkJson.decodeFromJsonElement(RealtimeEventDto.serializer(), element)
} catch (e: SerializationException) {
    null
} catch (e: IllegalArgumentException) {
    null
}
