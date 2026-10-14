package krio.systemdesign.chatapp.feature.chats.impl

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation
import krio.systemdesign.chatapp.crossfeature.domain.model.ConversationsSlice
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import krio.systemdesign.chatapp.crossfeature.domain.model.User
import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.crossfeature.domain.repository.BotsRepository
import krio.systemdesign.chatapp.crossfeature.domain.repository.ConversationsRepository
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import krio.systemdesign.chatapp.crossfeature.domain.repository.OutboxRepository
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository

// The chat list in memory: chats, how many are "cached", and whether loading more fails.
class TestConversationsRepository(chats: List<Conversation> = emptyList()) : ConversationsRepository {
    val chats = MutableStateFlow(chats)
    val isSynced = MutableStateFlow(true)
    var isOffline = false
    val ensured = mutableListOf<Int>()

    override fun observeConversations(
        offset: Int,
        count: Int,
    ): Flow<ConversationsSlice> = combine(chats, isSynced) { all, synced ->
        ConversationsSlice(items = all.drop(offset).take(count), hasMore = all.size > offset + count, isSynced = synced)
    }

    override suspend fun ensureLoaded(count: Int) {
        if (isOffline) throw IOException("offline")
        ensured += count
    }
}

// One chat's messages 1..lastSeq in memory, all "cached" without gaps.
class TestMessagesRepository(
    lastSeq: Long = 0,
    unreadCount: Int = 0,
) : MessagesRepository {
    val messages = MutableStateFlow((1..lastSeq).map { message(it) })
    val conversation = MutableStateFlow(
        Conversation(CHAT, "Anna", null, unreadCount, lastSeq, lastReadSeq = lastSeq - unreadCount, lastMessage = null),
    )
    var isOffline = false
    var refreshes = 0
    val reads = mutableListOf<Long>()

    // A new message from someone else at the end of the chat.
    fun receive(text: String) {
        val seq = messages.value.size + 1L
        messages.update { it + message(seq, text) }
        conversation.update { it.copy(lastSeq = seq) }
    }

    override fun observe(
        conversationId: String,
        window: Window,
    ): Flow<List<Message>> = messages.map { all ->
        all.filter { it.seq >= window.fromSeq && (window.toSeq == null || it.seq <= window.toSeq!!) }.reversed()
    }

    override fun observeConversation(conversationId: String): Flow<Conversation?> = conversation

    override suspend fun openLatest(conversationId: String): Window? {
        val top = messages.value.lastOrNull()?.seq ?: return null
        return Window(maxOf(1, top - PAGE + 1), null)
    }

    override suspend fun refreshLatest(conversationId: String) {
        if (isOffline) throw IOException("offline")
        refreshes++
    }

    override suspend fun openAround(
        conversationId: String,
        seq: Long,
    ): Window? {
        val last = conversation.value.lastSeq
        if (seq > last) return null
        val to = seq + PAGE / 2
        return Window(maxOf(1, seq - PAGE / 2), if (to >= last) null else to)
    }

    override suspend fun loadOlder(
        conversationId: String,
        beforeSeq: Long,
    ): Long {
        if (isOffline) throw IOException("offline")
        return maxOf(1, beforeSeq - PAGE)
    }

    override suspend fun loadNewer(
        conversationId: String,
        afterSeq: Long,
    ): Long? {
        if (isOffline) throw IOException("offline")
        val to = afterSeq + PAGE
        return if (to >= conversation.value.lastSeq) null else to
    }

    override suspend fun markRead(
        conversationId: String,
        seq: Long,
    ) {
        if (isOffline) throw IOException("offline")
        reads += seq
    }

    companion object {
        const val PAGE = 50
    }
}

class TestOutboxRepository : OutboxRepository {
    val outgoing = MutableStateFlow<List<OutgoingMessage>>(emptyList())
    val retried = mutableListOf<String>()

    override fun observe(conversationId: String): Flow<List<OutgoingMessage>> = outgoing

    override suspend fun enqueue(
        conversationId: String,
        text: String,
    ) {
        outgoing.update { it + OutgoingMessage("c${it.size}", conversationId, text.trim(), 0, isFailed = false) }
    }

    override suspend fun retry(clientId: String) {
        retried += clientId
    }
}

class TestSessionRepository : SessionRepository {
    override val session = MutableStateFlow<Session?>(Session("kate", "Kate"))
    var logouts = 0

    override suspend fun login(user: User) = Unit

    override suspend fun logout() {
        logouts++
        session.value = null
    }
}

fun message(
    seq: Long,
    text: String = "Message $seq",
    isMine: Boolean = false,
) = Message("$CHAT:$seq", CHAT, seq, if (isMine) "kate" else "anna", "Anna", isMine, text, seq * 60_000, null, false)

fun conversation(
    id: String,
    lastMessage: Message? = null,
    unreadCount: Int = 0,
) = Conversation(id, id, null, unreadCount, lastMessage?.seq ?: 0, lastMessage?.seq ?: 0, lastMessage)

const val CHAT = "anna"

class TestBotsRepository : BotsRepository {
    override val isEnabled = MutableStateFlow<Boolean?>(true)

    // Set to make the next switch fail as with no network.
    var isOffline = false

    override suspend fun setEnabled(enabled: Boolean): Boolean {
        if (isOffline) throw IOException("offline")
        isEnabled.value = enabled
        return enabled
    }
}
