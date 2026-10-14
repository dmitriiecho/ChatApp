package krio.systemdesign.chatapp.crossfeature.data.sync

import androidx.room3.withWriteTransaction
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import krio.systemdesign.chatapp.contract.SendMessageResponseDto
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.OutgoingMessageEntity
import krio.systemdesign.chatapp.crossfeature.data.remote.ChatApi
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import krio.systemdesign.chatapp.crossfeature.data.repository.MessagesRepositoryImpl
import krio.systemdesign.chatapp.crossfeature.data.repository.tryNetwork
import org.koin.core.annotation.Singleton

// Takes the outbox to the server, oldest first, one at a time, so messages arrive in the order they were written.
// Called by the outbox worker (WorkManager in :app), which retries after an IOException with a growing pause.
// Class public, constructor internal: the worker in :app calls it.
@Singleton
class OutboxSender internal constructor(
    private val api: ChatApi,
    private val db: ChatDatabase,
    private val messages: MessagesRepositoryImpl,
) {
    private val outboxDao = db.outboxDao()
    private val messageDao = db.messageDao()
    private val conversationDao = db.conversationDao()

    // Two drains at once would send the same message twice; the clientId would make the server ignore the second,
    // but the mutex spares the request.
    private val mutex = Mutex()

    // Sends until the outbox is empty. On a network failure the waiting messages show as not sent and the
    // IOException goes to the caller, who retries later.
    suspend fun drain() = mutex.withLock {
        while (true) {
            val next = outboxDao.oldest() ?: return@withLock
            val response = try {
                api.send(next.conversationId, next.clientId, next.text)
            } catch (e: IOException) {
                if (e.isRejection()) {
                    // The server will never take it (the chat is gone): retrying would block the queue forever.
                    outboxDao.delete(next.clientId)
                    continue
                }
                outboxDao.failAll()
                throw e
            }
            save(next, response)
        }
    }

    // The sent message into the cache, so it doesn't disappear when its outbox row goes. It is cached only as a
    // continuation of the cached chat (a lone message would be a piece of history with a gap below it): when
    // someone else's messages came in while offline and aren't cached, the gap is loaded first.
    private suspend fun save(
        sent: OutgoingMessageEntity,
        response: SendMessageResponseDto,
    ) {
        val conversationId = sent.conversationId
        val seq = response.message.seq
        var cachedMax = messageDao.maxSeq(conversationId)
        var pages = 0
        while (cachedMax != null && seq > cachedMax + 1 && pages < MAX_GAP_PAGES) {
            val error = tryNetwork {
                val page = api.after(conversationId, afterSeq = cachedMax, limit = MessagesRepositoryImpl.PAGE_SIZE)
                messageDao.upsertNewer(page.items.map { it.toEntity() })
            }
            if (error != null) break
            cachedMax = messageDao.maxSeq(conversationId)
            pages++
        }
        val continuesCache = cachedMax != null && seq <= cachedMax + 1
        db.withWriteTransaction {
            outboxDao.delete(sent.clientId)
            if (continuesCache) messageDao.upsertNewer(listOf(response.message.toEntity()))
            conversationDao.upsertNewer(listOf(response.conversation.toEntity()))
        }
        // Still a gap: the newest page instead, and the chat's window moves to it (ChatPaginator.refreshLatest).
        if (cachedMax != null && !continuesCache) tryNetwork { messages.refreshLatest(conversationId) }
    }

    // The server refused this message for good, unlike a lost connection, an outage, a wrong key or a rate limit.
    private fun IOException.isRejection(): Boolean {
        val status = (cause as? ClientRequestException)?.response?.status ?: return false
        return status in REJECTIONS
    }

    private companion object {
        const val MAX_GAP_PAGES = 3
        val REJECTIONS = setOf(
            HttpStatusCode.BadRequest,
            HttpStatusCode.Forbidden,
            HttpStatusCode.NotFound,
            HttpStatusCode.PayloadTooLarge,
        )
    }
}
