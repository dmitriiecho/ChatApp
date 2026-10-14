package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// Read up to seq, here and on the user's other devices. IOException offline.
@Factory
internal class MarkConversationReadUseCase(private val repository: MessagesRepository) {
    suspend operator fun invoke(
        conversationId: String,
        seq: Long,
    ) = repository.markRead(conversationId, seq)
}
