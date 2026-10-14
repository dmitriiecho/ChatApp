package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ConversationListMetaDao {
    @Query("SELECT * FROM conversation_list_meta WHERE id = 0")
    suspend fun get(): ConversationListMetaEntity?

    @Query("SELECT * FROM conversation_list_meta WHERE id = 0")
    fun observe(): Flow<ConversationListMetaEntity?>

    @Upsert
    suspend fun upsert(meta: ConversationListMetaEntity)

    @Query("DELETE FROM conversation_list_meta")
    suspend fun delete()
}

@Dao
internal interface ChatSyncStateDao {
    @Query("SELECT * FROM chat_sync_state WHERE conversationId = :conversationId")
    suspend fun get(conversationId: String): ChatSyncStateEntity?

    @Query("SELECT conversationId FROM chat_sync_state ORDER BY lastOpenedAtMillis DESC LIMIT :limit")
    suspend fun recentlyOpened(limit: Int): List<String>

    // Each field is updated on its own, not by reading the row and writing it back: the token and the opening
    // time change in different coroutines, and a whole-row write of one would undo the other.

    @Query(
        """INSERT OR IGNORE INTO chat_sync_state (conversationId, messagesSyncToken, lastOpenedAtMillis)
           VALUES (:conversationId, NULL, 0)""",
    )
    suspend fun ensureExists(conversationId: String)

    @Query("UPDATE chat_sync_state SET messagesSyncToken = :token WHERE conversationId = :conversationId")
    suspend fun setToken(
        conversationId: String,
        token: String,
    )

    @Query("UPDATE chat_sync_state SET lastOpenedAtMillis = :millis WHERE conversationId = :conversationId")
    suspend fun setLastOpened(
        conversationId: String,
        millis: Long,
    )

    @Query("DELETE FROM chat_sync_state WHERE conversationId IN (:conversationIds)")
    suspend fun deleteByConversations(conversationIds: List<String>)

    @Query("DELETE FROM chat_sync_state")
    suspend fun deleteAll()
}

@Dao
internal interface OutboxDao {
    @Query("SELECT * FROM outgoing_messages WHERE conversationId = :conversationId ORDER BY createdAtMillis, clientId")
    fun observe(conversationId: String): Flow<List<OutgoingMessageEntity>>

    @Query("SELECT * FROM outgoing_messages ORDER BY createdAtMillis, clientId LIMIT 1")
    suspend fun oldest(): OutgoingMessageEntity?

    @Upsert
    suspend fun upsert(message: OutgoingMessageEntity)

    @Query("UPDATE outgoing_messages SET isFailed = :isFailed WHERE clientId = :clientId")
    suspend fun setFailed(
        clientId: String,
        isFailed: Boolean,
    )

    @Query("UPDATE outgoing_messages SET isFailed = 1")
    suspend fun failAll()

    @Query("DELETE FROM outgoing_messages WHERE clientId = :clientId")
    suspend fun delete(clientId: String)

    @Query("DELETE FROM outgoing_messages")
    suspend fun deleteAll()
}

@Dao
internal interface SessionDao {
    @Query("SELECT * FROM session WHERE id = 0")
    fun observe(): Flow<SessionEntity?>

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM session")
    suspend fun delete()
}
