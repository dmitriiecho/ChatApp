package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation
import krio.systemdesign.chatapp.crossfeature.domain.repository.MessagesRepository
import org.koin.core.annotation.Factory

// The chat itself: its title, lastSeq (is there more below the window?) and lastReadSeq.
@Factory
internal class ObserveConversationUseCase(private val repository: MessagesRepository) {
    operator fun invoke(conversationId: String): Flow<Conversation?> = repository.observeConversation(conversationId)
}
