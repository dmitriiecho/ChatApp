package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// The chat from the server (the chat, its edits, its newest page), then a window on its newest messages.
// IOException offline.
@Factory
internal class RefreshLatestMessagesUseCase(private val repository: MessagesRepository) {
    suspend operator fun invoke(conversationId: String): Window? {
        repository.refreshLatest(conversationId)
        return repository.openLatest(conversationId)
    }
}
