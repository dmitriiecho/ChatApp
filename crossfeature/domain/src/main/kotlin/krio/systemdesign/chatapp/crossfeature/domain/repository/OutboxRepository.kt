package krio.systemdesign.chatapp.crossfeature.domain.repository

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage

// Messages waiting to be sent. Kept in the cache, so they survive the app being closed, and sent in order.
interface OutboxRepository {

    // Oldest first.
    fun observe(conversationId: String): Flow<List<OutgoingMessage>>

    suspend fun enqueue(
        conversationId: String,
        text: String,
    )

    suspend fun retry(clientId: String)
}
