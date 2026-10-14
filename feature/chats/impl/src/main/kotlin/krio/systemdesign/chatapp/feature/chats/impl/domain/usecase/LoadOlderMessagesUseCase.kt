package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// The window's new fromSeq, older than beforeSeq: from the cache when it has them, else from the server.
@Factory
internal class LoadOlderMessagesUseCase(private val repository: MessagesRepository) {
    suspend operator fun invoke(
        conversationId: String,
        beforeSeq: Long,
    ): Long = repository.loadOlder(conversationId, beforeSeq)
}
