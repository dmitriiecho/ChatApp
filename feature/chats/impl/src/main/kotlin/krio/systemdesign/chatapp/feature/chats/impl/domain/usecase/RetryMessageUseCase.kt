package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.OutboxRepository
import org.koin.core.annotation.Factory

@Factory
internal class RetryMessageUseCase(private val repository: OutboxRepository) {
    suspend operator fun invoke(clientId: String) = repository.retry(clientId)
}
