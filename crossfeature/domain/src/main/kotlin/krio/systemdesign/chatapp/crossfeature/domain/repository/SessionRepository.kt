package krio.systemdesign.chatapp.crossfeature.domain.repository

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import krio.systemdesign.chatapp.crossfeature.domain.model.User

interface SessionRepository {

    // Null when nobody is signed in. Kept across app restarts.
    val session: Flow<Session?>

    suspend fun login(user: User)

    // Stops the user's background work and drops everything cached for them.
    suspend fun logout()
}
