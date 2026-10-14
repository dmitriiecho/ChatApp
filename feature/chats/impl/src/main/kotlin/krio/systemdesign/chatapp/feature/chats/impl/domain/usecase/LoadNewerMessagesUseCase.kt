package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// The window's new toSeq, newer than afterSeq; null when it reached the chat's end.
@Factory
internal class LoadNewerMessagesUseCase(private val repository: MessagesRepository) {
    suspend operator fun invoke(
        conversationId: String,
        afterSeq: Long,
    ): Long? = repository.loadNewer(conversationId, afterSeq)
}
