package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import org.koin.core.annotation.Factory

// Signs out; the app then opens the sign-in, as it follows the session.
@Factory
internal class LogoutUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke() = repository.logout()
}
