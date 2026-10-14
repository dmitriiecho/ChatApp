package krio.systemdesign.chatapp.crossfeature.data.repository

import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.toDomain
import krio.systemdesign.chatapp.crossfeature.data.remote.ChatApi
import krio.systemdesign.chatapp.crossfeature.data.remote.ConversationsApi
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import krio.systemdesign.chatapp.crossfeature.data.session.SessionStore
import krio.systemdesign.chatapp.crossfeature.data.sync.MessageChangesSync
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Singleton

// The network only writes into Room; the screens read Room through a window. Loading older or newer messages
// takes them from the cache when it has them without a gap, and from the server otherwise.
@Singleton
internal class MessagesRepositoryImpl(
    private val chatApi: ChatApi,
    private val conversationsApi: ConversationsApi,
    private val messageChangesSync: MessageChangesSync,
    private val session: SessionStore,
    db: ChatDatabase,
) : MessagesRepository {

    private val messageDao = db.messageDao()
    private val conversationDao = db.conversationDao()

    override fun observe(
        conversationId: String,
        window: Window,
    ): Flow<List<Message>> = messageDao.observeWindow(conversationId, window.fromSeq, window.toSeq)
        .map { rows -> rows.map { it.toDomain(session.current.value?.userId) } }

    override fun observeConversation(conversationId: String): Flow<Conversation?> =
        conversationDao.observe(conversationId).map { it?.toDomain(session.current.value?.userId) }

    // The cache only: the newest messages it has.
    override suspend fun openLatest(conversationId: String): Window? {
        val topSeq = messageDao.maxSeq(conversationId) ?: return null
        val fromSeq = cachedDownFrom(conversationId, topSeq, notBelow = max(1, topSeq - PAGE_SIZE + 1))
        return Window(fromSeq = fromSeq, toSeq = null)
    }

    // The network only: everything fresh into Room; the screen updates through observe.
    override suspend fun refreshLatest(conversationId: String) {
        messageChangesSync.markOpened(conversationId)
        // The chat itself, for its lastSeq: opened from a link it may not be cached.
        conversationDao.upsertNewer(listOf(conversationsApi.conversation(conversationId).toEntity()))
        // Edits of the cached messages, before the page: at a chat's first sync the token is then taken before the
        // page, so no edit between the two is lost.
        messageChangesSync.sync(conversationId)
        save(chatApi.latest(conversationId, limit = PAGE_SIZE))
    }

    override suspend fun openAround(
        conversationId: String,
        seq: Long,
    ): Window? {
        if (!messageDao.isCached(conversationId, seq)) {
            save(chatApi.around(conversationId, seq, PAGE_SIZE))
            if (!messageDao.isCached(conversationId, seq)) return null
        }
        // Not past the gapless run of the cache around the anchor.
        val fromSeq = cachedDownFrom(conversationId, seq, notBelow = max(1, seq - PAGE_SIZE / 2))
        val toSeq = cachedUpFrom(conversationId, seq, notAbove = seq + PAGE_SIZE / 2)
        return Window(fromSeq = fromSeq, toSeq = if (toSeq >= lastSeq(conversationId)) null else toSeq)
    }

    override suspend fun loadOlder(
        conversationId: String,
        beforeSeq: Long,
    ): Long {
        if (beforeSeq <= 1) return beforeSeq
        val targetSeq = max(1, beforeSeq - PAGE_SIZE)
        val isCached = messageDao.count(conversationId, targetSeq, beforeSeq - 1) == (beforeSeq - targetSeq).toInt()
        val error = if (isCached) null else tryNetwork { save(chatApi.before(conversationId, beforeSeq, PAGE_SIZE)) }
        // Offline the window grows by as much as the cache has without a gap.
        val newFromSeq = cachedDownFrom(conversationId, beforeSeq, notBelow = targetSeq)
        if (newFromSeq == beforeSeq && error != null) throw error
        return newFromSeq
    }

    override suspend fun loadNewer(
        conversationId: String,
        afterSeq: Long,
    ): Long? {
        val lastSeq = lastSeq(conversationId)
        val targetSeq = min(afterSeq + PAGE_SIZE, lastSeq)
        val isCached = messageDao.count(conversationId, afterSeq + 1, targetSeq) == (targetSeq - afterSeq).toInt()
        val error = if (isCached) null else tryNetwork { save(chatApi.after(conversationId, afterSeq, PAGE_SIZE)) }
        val newToSeq = cachedUpFrom(conversationId, afterSeq, notAbove = afterSeq + PAGE_SIZE)
        if (newToSeq == afterSeq && error != null) throw error
        return if (newToSeq >= lastSeq(conversationId)) null else newToSeq
    }

    override suspend fun markRead(
        conversationId: String,
        seq: Long,
    ) {
        conversationDao.upsertNewer(listOf(chatApi.read(conversationId, seq).toEntity()))
    }

    // How far down from seq the cache has messages without a gap, no further than notBelow. seq must be cached.
    private suspend fun cachedDownFrom(
        conversationId: String,
        seq: Long,
        notBelow: Long,
    ): Long = messageDao.runStart(conversationId, seq, notBelow) ?: notBelow

    // How far up from seq the cache has messages without a gap, no further than notAbove. seq must be cached.
    private suspend fun cachedUpFrom(
        conversationId: String,
        seq: Long,
        notAbove: Long,
    ): Long = messageDao.runEnd(conversationId, seq, notAbove) ?: notAbove

    private suspend fun save(page: MessagesPageDto) = messageDao.upsertNewer(page.items.map { it.toEntity() })

    private suspend fun lastSeq(conversationId: String): Long = conversationDao.lastSeq(conversationId) ?: 0

    companion object {
        const val PAGE_SIZE = 50
    }
}

// The error instead of throwing it, for a call whose failure is reported only if the cache can't stand in.
internal suspend inline fun tryNetwork(block: suspend () -> Unit): IOException? = try {
    block()
    null
} catch (e: IOException) {
    e
}
