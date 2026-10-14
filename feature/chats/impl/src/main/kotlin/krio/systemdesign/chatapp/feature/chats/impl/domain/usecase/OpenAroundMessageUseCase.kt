package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// A window around seq, loading it when it isn't cached: the chat opens at its first unread message.
// Null when there is no such message. IOException offline when it isn't cached.
@Factory
internal class OpenAroundMessageUseCase(private val repository: MessagesRepository) {
    suspend operator fun invoke(
        conversationId: String,
        seq: Long,
    ): Window? = repository.openAround(conversationId, seq)
}
