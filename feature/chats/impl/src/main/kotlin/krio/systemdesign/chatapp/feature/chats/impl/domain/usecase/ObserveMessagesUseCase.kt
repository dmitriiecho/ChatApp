package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// The window's messages, newest first: a subscription to the cache, which everything new reaches through.
@Factory
internal class ObserveMessagesUseCase(private val repository: MessagesRepository) {
    operator fun invoke(
        conversationId: String,
        window: Window,
    ): Flow<List<Message>> = repository.observe(conversationId, window)
}
