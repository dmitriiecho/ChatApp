package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage
import krio.systemdesign.chatapp.crossfeature.domain.repository.OutboxRepository
import org.koin.core.annotation.Factory

// The chat's messages not on the server yet, oldest first.
@Factory
internal class ObserveOutgoingMessagesUseCase(private val repository: OutboxRepository) {
    operator fun invoke(conversationId: String): Flow<List<OutgoingMessage>> = repository.observe(conversationId)
}
