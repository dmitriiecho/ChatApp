package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// A window on the newest cached messages, without the network: the chat opens at once and works offline.
@Factory
internal class OpenLatestMessagesUseCase(private val repository: MessagesRepository) {
    suspend operator fun invoke(conversationId: String): Window? = repository.openLatest(conversationId)
}
