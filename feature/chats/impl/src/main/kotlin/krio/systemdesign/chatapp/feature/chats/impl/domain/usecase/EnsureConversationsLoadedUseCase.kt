package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.ConversationsRepository
import org.koin.core.annotation.Factory

// At least count chats cached from the top, so the list window can move down onto them. IOException offline.
@Factory
internal class EnsureConversationsLoadedUseCase(private val repository: ConversationsRepository) {
    suspend operator fun invoke(count: Int) = repository.ensureLoaded(count)
}
