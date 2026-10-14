package krio.systemdesign.chatapp.crossfeature.domain.repository

import krio.systemdesign.chatapp.crossfeature.domain.model.User

interface UsersRepository {

    // Who one can sign in as. IOException without a network.
    suspend fun users(): List<User>
}
