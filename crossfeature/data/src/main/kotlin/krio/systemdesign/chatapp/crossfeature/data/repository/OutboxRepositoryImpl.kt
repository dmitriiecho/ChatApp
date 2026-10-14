package krio.systemdesign.chatapp.crossfeature.data.repository

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import krio.systemdesign.chatapp.crossfeature.data.Clock
import krio.systemdesign.chatapp.crossfeature.data.OutboxScheduler
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.OutgoingMessageEntity
import krio.systemdesign.chatapp.crossfeature.data.local.toDomain
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage
import krio.systemdesign.chatapp.crossfeature.domain.repository.OutboxRepository
import org.koin.core.annotation.Singleton

// A sent message is written here first and shows at once; OutboxSender takes it to the server when it can.
@Singleton
internal class OutboxRepositoryImpl(
    db: ChatDatabase,
    private val scheduler: OutboxScheduler,
    private val clock: Clock,
) : OutboxRepository {

    private val outboxDao = db.outboxDao()

    override fun observe(conversationId: String): Flow<List<OutgoingMessage>> =
        outboxDao.observe(conversationId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun enqueue(
        conversationId: String,
        text: String,
    ) {
        val message = OutgoingMessageEntity(
            clientId = UUID.randomUUID().toString(),
            conversationId = conversationId,
            text = text.trim(),
            createdAtMillis = clock.nowMillis(),
            isFailed = false,
        )
        outboxDao.upsert(message)
        scheduler.schedule()
    }

    // The whole queue goes again: it is sent in order, so nothing after a failed message went either.
    override suspend fun retry(clientId: String) {
        outboxDao.setFailed(clientId, isFailed = false)
        scheduler.schedule()
    }
}
