package krio.systemdesign.chatapp.crossfeature.data.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import krio.systemdesign.chatapp.crossfeature.data.DataScope
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import org.koin.core.annotation.Singleton

// The signed-in user as kept in the database. On its own, not in SessionRepositoryImpl: the HTTP client needs it for
// its headers, and the repository needs the app's background work, which needs the HTTP client.
@Singleton
internal class SessionStore(
    db: ChatDatabase,
    dataScope: DataScope,
) {
    val session: Flow<Session?> = db.sessionDao().observe().map { it?.let { row -> Session(row.userId, row.userName) } }

    // The session at hand without suspending, for request headers and for telling one's own messages.
    val current: StateFlow<Session?> = session.stateIn(dataScope.scope, SharingStarted.Eagerly, null)
}
