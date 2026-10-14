package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.ConversationsSlice
import krio.systemdesign.chatapp.crossfeature.domain.repository.ConversationsRepository
import org.koin.core.annotation.Factory

// A window of the chat list, count chats from position offset, re-sent on every change of the cache.
@Factory
internal class ObserveConversationsUseCase(private val repository: ConversationsRepository) {
    operator fun invoke(
        offset: Int,
        count: Int,
    ): Flow<ConversationsSlice> = repository.observeConversations(offset, count)
}
