package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import org.koin.core.annotation.Factory

@Factory
internal class ObserveSessionUseCase(private val repository: SessionRepository) {
    operator fun invoke(): Flow<Session?> = repository.session
}
