package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class ConversationDao {

    // What the list shows: limit chats from position offset among those above the loaded boundary, most active
    // first. Re-sorting happens here: a new message raises a chat's sortKey and Room re-reads this query.
    // Room re-reads it on every change of the tables it reads, but LIMIT/OFFSET keeps that to the window's rows
    // however many chats are cached; OFFSET skips rows along the sortKey index.
    @Query(
        """SELECT * FROM conversations
           WHERE sortKey >= (SELECT loadedDownToSortKey FROM conversation_list_meta WHERE id = 0)
           ORDER BY sortKey DESC LIMIT :limit OFFSET :offset""",
    )
    abstract fun observeLoaded(
        offset: Int,
        limit: Int,
    ): Flow<List<ConversationEntity>>

    // How many chats are loaded without gaps from the top.
    @Query(
        """SELECT COUNT(*) FROM conversations
           WHERE sortKey >= (SELECT loadedDownToSortKey FROM conversation_list_meta WHERE id = 0)""",
    )
    abstract suspend fun countLoaded(): Int

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    abstract fun observe(conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT lastSeq FROM conversations WHERE id = :conversationId")
    abstract suspend fun lastSeq(conversationId: String): Long?

    @Query("SELECT version FROM conversations WHERE id = :conversationId")
    abstract suspend fun version(conversationId: String): Long?

    // Writes the chats without overwriting newer data with older: a page may leave the server before a new message
    // and arrive after the socket brought it; its older sortKey would move the chat back down.
    @Transaction
    open suspend fun upsertNewer(items: List<ConversationEntity>) {
        val cachedVersions = versionsOf(items.map { it.id }).associate { it.id to it.version }
        upsert(items.filter { it.version >= (cachedVersions[it.id] ?: Long.MIN_VALUE) })
    }

    // For syncs and the socket: keeps a chat only if it is cached already or rose above the loaded boundary.
    // Chats deep down that the user hasn't scrolled to aren't needed: paging brings them fresh when it gets there.
    @Transaction
    open suspend fun upsertIfRelevant(items: List<ConversationEntity>) {
        val boundary = loadedDownToSortKey()
        val cachedIds = existingIds(items.map { it.id }).toSet()
        upsertNewer(items.filter { it.id in cachedIds || (boundary != null && it.sortKey >= boundary) })
    }

    @Query("DELETE FROM conversations WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM conversations")
    abstract suspend fun deleteAll()

    @Query("SELECT loadedDownToSortKey FROM conversation_list_meta WHERE id = 0")
    protected abstract suspend fun loadedDownToSortKey(): Long?

    @Query("SELECT id FROM conversations WHERE id IN (:ids)")
    protected abstract suspend fun existingIds(ids: List<String>): List<String>

    @Upsert
    protected abstract suspend fun upsert(items: List<ConversationEntity>)

    @Query("SELECT id, version FROM conversations WHERE id IN (:ids)")
    protected abstract suspend fun versionsOf(ids: List<String>): List<IdAndVersion>
}
