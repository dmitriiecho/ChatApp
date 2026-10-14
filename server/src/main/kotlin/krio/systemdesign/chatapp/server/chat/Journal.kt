package krio.systemdesign.chatapp.server.chat

import krio.systemdesign.chatapp.server.db.Transaction

// The change logs behind sync tokens: one per user for the chat list, one per chat for message edits.
// A token is a position in its log, as text: "everything up to here is already applied". The client keeps it as is.
// Each log keeps its last [limit] entries; older ones are dropped and the log's floor moves up, so a token below
// the floor gets resetRequired: the changes after it are gone.
internal class Journal(private val limit: Int) {

    // The token a client starts from: the newest position, so it sees only what changes from now on.
    fun conversationsHead(tx: Transaction): String = head(tx, CONVERSATIONS).toString()

    fun messagesHead(tx: Transaction): String = head(tx, MESSAGES).toString()

    fun recordConversation(
        tx: Transaction,
        userId: String,
        conversationId: String,
        removed: Boolean = false,
    ) {
        tx.insert(
            "INSERT INTO conversation_journal(userId, conversationId, removed) VALUES (?, ?, ?)",
            userId,
            conversationId,
            removed,
        )
        compact(tx, CONVERSATIONS, column = "userId", key = userId, scope = userScope(userId))
    }

    fun recordMessage(
        tx: Transaction,
        conversationId: String,
        messageId: String,
    ) {
        tx.insert("INSERT INTO message_journal(conversationId, messageId) VALUES (?, ?)", conversationId, messageId)
        compact(tx, MESSAGES, column = "conversationId", key = conversationId, scope = chatScope(conversationId))
    }

    // The entries of a user's chat list log after the token, oldest first; null when the token can't be used.
    fun conversationEntries(
        tx: Transaction,
        userId: String,
        token: String,
        limit: Int,
    ): Entries<ConversationEntry>? {
        val position = validPosition(tx, token, CONVERSATIONS, userScope(userId)) ?: return null
        val rows = tx.query(
            """SELECT position, conversationId, removed FROM conversation_journal
               WHERE userId = ? AND position > ? ORDER BY position LIMIT ?""",
            userId,
            position,
            limit + 1,
        ) { ConversationEntry(it.getLong("position"), it.getString("conversationId"), it.getBoolean("removed")) }
        val batch = rows.take(limit)
        return Entries(batch, next = batch.lastOrNull()?.position ?: position, hasMore = rows.size > limit)
    }

    fun messageEntries(
        tx: Transaction,
        conversationId: String,
        token: String,
        limit: Int,
    ): Entries<MessageEntry>? {
        val position = validPosition(tx, token, MESSAGES, chatScope(conversationId)) ?: return null
        val rows = tx.query(
            """SELECT position, messageId FROM message_journal
               WHERE conversationId = ? AND position > ? ORDER BY position LIMIT ?""",
            conversationId,
            position,
            limit + 1,
        ) { MessageEntry(it.getLong("position"), it.getString("messageId")) }
        val batch = rows.take(limit)
        return Entries(batch, next = batch.lastOrNull()?.position ?: position, hasMore = rows.size > limit)
    }

    // Not a number, below the log's floor (its changes were dropped) or past the newest position (the server's
    // database was recreated): the client has to start over.
    private fun validPosition(
        tx: Transaction,
        token: String,
        table: String,
        scope: String,
    ): Long? {
        val position = token.toLongOrNull() ?: return null
        val floor = tx.queryOne("SELECT floor FROM journal_floors WHERE scope = ?", scope) { it.getLong(1) } ?: 0
        return position.takeIf { it in floor..head(tx, table) }
    }

    // Drops the oldest entries of one log past the limit and raises its floor to the last dropped position.
    private fun compact(
        tx: Transaction,
        table: String,
        column: String,
        key: String,
        scope: String,
    ) {
        val count = tx.queryOne("SELECT COUNT(*) FROM $table WHERE $column = ?", key) { it.getLong(1) }!!
        if (count <= limit) return
        val lastDropped = tx.queryOne(
            "SELECT position FROM $table WHERE $column = ? ORDER BY position LIMIT 1 OFFSET ?",
            key,
            count - limit - 1,
        ) { it.getLong(1) }!!
        tx.update("DELETE FROM $table WHERE $column = ? AND position <= ?", key, lastDropped)
        tx.update(
            """INSERT INTO journal_floors(scope, floor) VALUES (?, ?)
               ON CONFLICT(scope) DO UPDATE SET floor = MAX(floor, excluded.floor)""",
            scope,
            lastDropped,
        )
    }

    // sqlite_sequence keeps the last position even after compaction has deleted the rows.
    private fun head(
        tx: Transaction,
        table: String,
    ): Long = tx.queryOne("SELECT seq FROM sqlite_sequence WHERE name = ?", table) { it.getLong(1) } ?: 0

    private fun userScope(userId: String) = "user:$userId"

    private fun chatScope(conversationId: String) = "chat:$conversationId"

    private companion object {
        const val CONVERSATIONS = "conversation_journal"
        const val MESSAGES = "message_journal"
    }
}

internal data class ConversationEntry(
    val position: Long,
    val conversationId: String,
    val removed: Boolean,
)

internal data class MessageEntry(
    val position: Long,
    val messageId: String,
)

// A batch of log entries. next is the position to save once it is applied: the last entry's, or the token's
// own when nothing changed.
internal data class Entries<T : Any>(
    val items: List<T>,
    val next: Long,
    val hasMore: Boolean,
)
