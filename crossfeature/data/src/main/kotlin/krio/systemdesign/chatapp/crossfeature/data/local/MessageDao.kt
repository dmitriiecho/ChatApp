package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class MessageDao {

    // Writes the messages without overwriting newer data with older: a page may leave the server before an edit
    // and arrive after the socket brought that edit. Repeats are harmless too: a sync resends what the socket did.
    @Transaction
    open suspend fun upsertNewer(items: List<MessageEntity>) {
        val cachedVersions = versionsOf(items.map { it.id }).associate { it.id to it.version }
        upsert(items.filter { it.version >= (cachedVersions[it.id] ?: Long.MIN_VALUE) })
    }

    // The same for messages already cached only: edits of messages not loaded aren't needed.
    @Transaction
    open suspend fun updateExistingNewer(items: List<MessageEntity>) {
        val cachedVersions = versionsOf(items.map { it.id }).associate { it.id to it.version }
        update(items.filter { item -> cachedVersions[item.id]?.let { item.version >= it } == true })
    }

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE conversationId = :conversationId AND seq = :seq)")
    abstract suspend fun isCached(
        conversationId: String,
        seq: Long,
    ): Boolean

    @Query("SELECT MAX(seq) FROM messages WHERE conversationId = :conversationId")
    abstract suspend fun maxSeq(conversationId: String): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND seq BETWEEN :fromSeq AND :toSeq")
    abstract suspend fun count(
        conversationId: String,
        fromSeq: Long,
        toSeq: Long,
    ): Int

    // The start of the gapless run of the cache that seq is in, looking no lower than notBelow; null when the run
    // goes below notBelow, so everything from notBelow to seq is cached. The bound keeps the query to one page
    // instead of every cached message under seq.
    @Query(
        """SELECT MAX(m.seq) FROM messages m
           WHERE m.conversationId = :conversationId AND m.seq BETWEEN :notBelow AND :seq
           AND NOT EXISTS (SELECT 1 FROM messages p WHERE p.conversationId = :conversationId AND p.seq = m.seq - 1)""",
    )
    abstract suspend fun runStart(
        conversationId: String,
        seq: Long,
        notBelow: Long,
    ): Long?

    // The end of the gapless run seq is in, looking no higher than notAbove; null when it goes past notAbove.
    @Query(
        """SELECT MIN(m.seq) FROM messages m
           WHERE m.conversationId = :conversationId AND m.seq BETWEEN :seq AND :notAbove
           AND NOT EXISTS (SELECT 1 FROM messages n WHERE n.conversationId = :conversationId AND n.seq = m.seq + 1)""",
    )
    abstract suspend fun runEnd(
        conversationId: String,
        seq: Long,
        notAbove: Long,
    ): Long?

    // The window [fromSeq, toSeq], newest first. toSeq == null: up to the end of the gapless run from fromSeq, so a
    // gap never shows.
    @Query(
        """SELECT * FROM messages
           WHERE conversationId = :conversationId AND seq >= :fromSeq
           AND seq <= COALESCE(:toSeq,
               (SELECT MIN(m.seq) FROM messages m
                WHERE m.conversationId = :conversationId AND m.seq >= :fromSeq
                AND NOT EXISTS (SELECT 1 FROM messages n WHERE n.conversationId = :conversationId AND n.seq = m.seq + 1)))
           ORDER BY seq DESC""",
    )
    abstract fun observeWindow(
        conversationId: String,
        fromSeq: Long,
        toSeq: Long?,
    ): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversationId IN (:conversationIds)")
    abstract suspend fun deleteByConversations(conversationIds: List<String>)

    @Query("DELETE FROM messages")
    abstract suspend fun deleteAll()

    @Upsert
    protected abstract suspend fun upsert(items: List<MessageEntity>)

    @Update
    protected abstract suspend fun update(items: List<MessageEntity>)

    @Query("SELECT id, version FROM messages WHERE id IN (:ids)")
    protected abstract suspend fun versionsOf(ids: List<String>): List<IdAndVersion>
}

internal data class IdAndVersion(
    val id: String,
    val version: Long,
)
