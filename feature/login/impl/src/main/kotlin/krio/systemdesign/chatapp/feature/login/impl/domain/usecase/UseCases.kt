package krio.systemdesign.chatapp.feature.login.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.model.User
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import krio.systemdesign.chatapp.crossfeature.domain.repository.UsersRepository
import org.koin.core.annotation.Factory

// Who one can sign in as. IOException without a network.
@Factory
internal class GetUsersUseCase(private val repository: UsersRepository) {
    suspend operator fun invoke(): List<User> = repository.users()
}

// Signs in; the app then opens the chats, as it follows the session.
@Factory
internal class LoginUseCase(private val repository: SessionRepository) {
    suspend operator fun invoke(user: User) = repository.login(user)
}
