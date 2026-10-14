package krio.systemdesign.chatapp.crossfeature.data.session

import androidx.room3.withWriteTransaction
import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.data.SessionWork
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.SessionEntity
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import krio.systemdesign.chatapp.crossfeature.domain.model.User
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import org.koin.core.annotation.Singleton

@Singleton
internal class SessionRepositoryImpl(
    private val db: ChatDatabase,
    private val sessionWork: SessionWork,
    sessionStore: SessionStore,
) : SessionRepository {

    override val session: Flow<Session?> = sessionStore.session

    override suspend fun login(user: User) {
        db.sessionDao().upsert(SessionEntity(userId = user.id, userName = user.name))
    }

    // The work first: nothing of the old user may be written after the cache is dropped.
    override suspend fun logout() {
        sessionWork.stop()
        db.withWriteTransaction {
            db.sessionDao().delete()
            db.conversationDao().deleteAll()
            db.conversationListMetaDao().delete()
            db.messageDao().deleteAll()
            db.chatSyncStateDao().deleteAll()
            db.outboxDao().deleteAll()
        }
    }
}
