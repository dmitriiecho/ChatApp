package krio.systemdesign.chatapp.crossfeature.data.session

import krio.systemdesign.chatapp.crossfeature.data.remote.ConversationsApi
import krio.systemdesign.chatapp.crossfeature.domain.model.User
import krio.systemdesign.chatapp.crossfeature.domain.repository.UsersRepository
import org.koin.core.annotation.Singleton

@Singleton
internal class UsersRepositoryImpl(private val api: ConversationsApi) : UsersRepository {
    override suspend fun users(): List<User> = api.users().map { User(id = it.id, name = it.name) }
}
