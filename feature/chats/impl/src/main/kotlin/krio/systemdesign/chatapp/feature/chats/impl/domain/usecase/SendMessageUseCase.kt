package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.OutboxRepository
import org.koin.core.annotation.Factory

// Into the outbox: the message shows at once and is sent when the network is there.
@Factory
internal class SendMessageUseCase(private val repository: OutboxRepository) {
    suspend operator fun invoke(
        conversationId: String,
        text: String,
    ) = repository.enqueue(conversationId, text)
}
