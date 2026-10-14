package krio.systemdesign.chatapp.server.chat

import java.sql.ResultSet
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.ConversationsPageDto
import krio.systemdesign.chatapp.contract.MessageChangesDto
import krio.systemdesign.chatapp.contract.MessageDto
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.contract.SendMessageResponseDto
import krio.systemdesign.chatapp.contract.UserDto
import krio.systemdesign.chatapp.server.db.Database
import krio.systemdesign.chatapp.server.db.Transaction
import krio.systemdesign.chatapp.server.db.getLongOrNull
import krio.systemdesign.chatapp.server.events.EventBus

// Everything the API and the bots do with the data. Each change bumps the version of what it touched, writes the
// change logs and, once committed, sends the events to the users' open sockets.
// A chat's sortKey, version and journal are kept for people only: bots have no devices to sync.
class ChatService(
    private val db: Database,
    private val events: EventBus,
    private val clock: () -> Long,
    journalLimit: Int = JOURNAL_LIMIT,
) {
    private val journal = Journal(journalLimit)

    private val _posted =
        MutableSharedFlow<MessageDto>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Messages people send, for the bots to answer. Missing one only means a bot stays silent.
    val posted: SharedFlow<MessageDto> = _posted

    suspend fun isUser(userId: String): Boolean = db.transaction {
        queryOne("SELECT 1 FROM users WHERE id = ? AND isBot = 0", userId) { true } ?: false
    }

    suspend fun users(): List<UserDto> = db.transaction {
        query("SELECT id, name FROM users WHERE isBot = 0 ORDER BY name") {
            UserDto(it.getString("id"), it.getString("name"))
        }
    }

    // Most active first; beforeSortKey continues below the previous page.
    suspend fun conversations(
        userId: String,
        beforeSortKey: Long?,
        limit: Int,
    ): ConversationsPageDto = db.transaction {
        val ids = query(
            "SELECT conversationId FROM participants WHERE userId = ? AND sortKey < ? ORDER BY sortKey DESC LIMIT ?",
            userId,
            beforeSortKey ?: Long.MAX_VALUE,
            limit + 1,
        ) { it.getString(1) }
        ConversationsPageDto(items = ids.take(limit).map { conversation(userId, it)!! }, hasMore = ids.size > limit)
    }

    // null: no such chat or the user isn't in it.
    suspend fun conversation(
        userId: String,
        conversationId: String,
    ): ConversationDto? = db.transaction { conversation(userId, conversationId) }

    // since == null: the client starts the log now, so it gets no changes and the current token.
    suspend fun conversationChanges(
        userId: String,
        since: String?,
        limit: Int,
    ): ConversationChangesDto = db.transaction {
        val head = journal.conversationsHead(this)
        if (since == null) return@transaction ConversationChangesDto(emptyList(), emptyList(), head, false, false)
        val entries = journal.conversationEntries(this, userId, since, limit)
            ?: return@transaction ConversationChangesDto(emptyList(), emptyList(), head, false, resetRequired = true)
        // A chat changed twice in the batch is sent once, as it is now.
        val latest = entries.items.associateBy { it.conversationId }.values
        val changed = latest.filterNot { it.removed }.mapNotNull { conversation(userId, it.conversationId) }
        val removedIds = latest.map { it.conversationId } - changed.map { it.id }.toSet()
        ConversationChangesDto(changed, removedIds, entries.next.toString(), entries.hasMore, resetRequired = false)
    }

    // Newest first. null: the user isn't in the chat.
    suspend fun messages(
        userId: String,
        conversationId: String,
        page: MessagesPage,
    ): MessagesPageDto? = db.transaction {
        if (!isMember(userId, conversationId)) return@transaction null
        val items = when (page) {
            is MessagesPage.Latest -> messagesWhere(
                "conversationId = ? ORDER BY seq DESC LIMIT ?",
                conversationId,
                page.limit,
            )
            is MessagesPage.Before -> messagesWhere(
                "conversationId = ? AND seq < ? ORDER BY seq DESC LIMIT ?",
                conversationId,
                page.seq,
                page.limit,
            )
            is MessagesPage.After -> messagesWhere(
                "conversationId = ? AND seq > ? ORDER BY seq ASC LIMIT ?",
                conversationId,
                page.seq,
                page.limit,
            ).reversed()
            // The message itself and the older half, then the newer half.
            is MessagesPage.Around -> messagesWhere(
                "conversationId = ? AND seq > ? ORDER BY seq ASC LIMIT ?",
                conversationId,
                page.seq,
                page.limit / 2,
            ).reversed() + messagesWhere(
                "conversationId = ? AND seq <= ? ORDER BY seq DESC LIMIT ?",
                conversationId,
                page.seq,
                page.limit - page.limit / 2,
            )
        }
        MessagesPageDto(items)
    }

    // Edits and deletions in one chat after the token. null: the user isn't in the chat.
    suspend fun messageChanges(
        userId: String,
        conversationId: String,
        since: String?,
        limit: Int,
    ): MessageChangesDto? = db.transaction {
        if (!isMember(userId, conversationId)) return@transaction null
        val head = journal.messagesHead(this)
        if (since == null) return@transaction MessageChangesDto(emptyList(), head, false, false)
        val entries = journal.messageEntries(this, conversationId, since, limit)
            ?: return@transaction MessageChangesDto(emptyList(), head, false, resetRequired = true)
        val changed = entries.items.map { it.messageId }.distinct().mapNotNull { message(it) }
        MessageChangesDto(changed, entries.next.toString(), entries.hasMore, resetRequired = false)
    }

    // A retry with a clientId already used returns the message sent then and changes nothing.
    // null: the author isn't in the chat.
    suspend fun send(
        authorId: String,
        conversationId: String,
        clientId: String?,
        text: String,
    ): SendMessageResponseDto? {
        val pending = mutableListOf<Pair<String, RealtimeEventDto>>()
        val response = db.transaction {
            if (!isMember(authorId, conversationId)) return@transaction null
            if (clientId != null) {
                val sent = queryOne("SELECT id FROM messages WHERE authorId = ? AND clientId = ?", authorId, clientId) {
                    it.getString(1)
                }
                if (sent !=
                    null
                ) {
                    return@transaction SendMessageResponseDto(
                        message(sent)!!,
                        conversation(authorId, conversationId)!!,
                    )
                }
            }
            val seq = queryOne("SELECT lastSeq FROM conversations WHERE id = ?", conversationId) { it.getLong(1) }!! + 1
            val id = "$conversationId:$seq"
            update(
                """INSERT INTO messages(id, conversationId, seq, version, authorId, clientId, text, sentAtMillis)
                   VALUES (?, ?, ?, 1, ?, ?, ?, ?)""",
                id,
                conversationId,
                seq,
                authorId,
                clientId,
                text,
                clock(),
            )
            update("UPDATE conversations SET lastSeq = ? WHERE id = ?", seq, conversationId)
            // What you wrote you have read.
            update(
                "UPDATE participants SET lastReadSeq = ? WHERE conversationId = ? AND userId = ?",
                seq,
                conversationId,
                authorId,
            )
            val message = message(id)!!
            for (userId in people(conversationId)) {
                touch(userId, conversationId, raise = true)
                pending += userId to RealtimeEventDto.MessageNew(message)
                pending += userId to RealtimeEventDto.ConversationUpdated(conversation(userId, conversationId)!!)
            }
            SendMessageResponseDto(message, conversation(authorId, conversationId)!!)
        } ?: return null
        pending.forEach { (userId, event) -> events.publish(userId, event) }
        if (isPerson(authorId)) _posted.tryEmit(response.message)
        return response
    }

    // Moves the user's read mark up to seq (never down, never past the last message). null: not in the chat.
    suspend fun markRead(
        userId: String,
        conversationId: String,
        seq: Long,
    ): ConversationDto? {
        var changed = false
        val conversation = db.transaction {
            if (!isMember(userId, conversationId)) return@transaction null
            val lastSeq = queryOne("SELECT lastSeq FROM conversations WHERE id = ?", conversationId) { it.getLong(1) }!!
            changed = update(
                "UPDATE participants SET lastReadSeq = ? WHERE conversationId = ? AND userId = ? AND lastReadSeq < ?",
                minOf(seq, lastSeq),
                conversationId,
                userId,
                minOf(seq, lastSeq),
            ) > 0
            if (changed) touch(userId, conversationId, raise = false)
            conversation(userId, conversationId)
        } ?: return null
        // Every device of the user: a chat read on the phone stops showing as unread on the tablet.
        if (changed) events.publish(userId, RealtimeEventDto.ConversationUpdated(conversation))
        return conversation
    }

    suspend fun edit(
        messageId: String,
        text: String,
    ) = change(messageId) {
        update("UPDATE messages SET text = ?, editedAtMillis = ? WHERE id = ?", text, clock(), messageId)
    }

    // The message stays as a tombstone: its seq doesn't go away, so clients see no gap.
    suspend fun delete(messageId: String) = change(messageId) {
        update("UPDATE messages SET text = '', isDeleted = 1 WHERE id = ?", messageId)
    }

    // An edit or a deletion: a new version, an entry in the chat's log, and the chat too if it was the last message.
    private suspend fun change(
        messageId: String,
        apply: Transaction.() -> Unit,
    ) {
        val pending = mutableListOf<Pair<String, RealtimeEventDto>>()
        db.transaction {
            val old = message(messageId) ?: return@transaction
            apply()
            update("UPDATE messages SET version = version + 1 WHERE id = ?", messageId)
            journal.recordMessage(this, old.conversationId, messageId)
            val message = message(messageId)!!
            val isLast =
                queryOne("SELECT lastSeq FROM conversations WHERE id = ?", old.conversationId) { it.getLong(1) } ==
                    old.seq
            for (userId in people(old.conversationId)) {
                pending += userId to RealtimeEventDto.MessageChanged(message)
                if (isLast) {
                    // The list shows the last message: the chat changed too, but doesn't move up.
                    touch(userId, old.conversationId, raise = false)
                    pending +=
                        userId to RealtimeEventDto.ConversationUpdated(conversation(userId, old.conversationId)!!)
                }
            }
        }
        pending.forEach { (userId, event) -> events.publish(userId, event) }
    }

    // A new version of the chat for the user and an entry in the user's chat list log; raise moves it to the top.
    private fun Transaction.touch(
        userId: String,
        conversationId: String,
        raise: Boolean,
    ) {
        if (raise) {
            update("UPDATE users SET lastSortKey = lastSortKey + 1 WHERE id = ?", userId)
            update(
                """UPDATE participants SET sortKey = (SELECT lastSortKey FROM users WHERE id = ?), version = version + 1
                   WHERE conversationId = ? AND userId = ?""",
                userId,
                conversationId,
                userId,
            )
        } else {
            update(
                "UPDATE participants SET version = version + 1 WHERE conversationId = ? AND userId = ?",
                conversationId,
                userId,
            )
        }
        journal.recordConversation(this, userId, conversationId)
    }

    private fun Transaction.isMember(
        userId: String,
        conversationId: String,
    ): Boolean =
        queryOne("SELECT 1 FROM participants WHERE conversationId = ? AND userId = ?", conversationId, userId) { true }
            ?: false

    private suspend fun isPerson(userId: String): Boolean = isUser(userId)

    // The chat's members who are people, not bots.
    private fun Transaction.people(conversationId: String): List<String> = query(
        "SELECT p.userId FROM participants p JOIN users u ON u.id = p.userId WHERE p.conversationId = ? AND u.isBot = 0",
        conversationId,
    ) { it.getString(1) }

    private fun Transaction.conversation(
        userId: String,
        conversationId: String,
    ): ConversationDto? = queryOne(
        """SELECT c.id, c.title, c.isGroup, c.lastSeq, p.sortKey, p.version, p.lastReadSeq,
               (SELECT u.name FROM participants o JOIN users u ON u.id = o.userId
                WHERE o.conversationId = c.id AND o.userId != p.userId LIMIT 1) AS otherName,
               (SELECT COUNT(*) FROM messages m
                WHERE m.conversationId = c.id AND m.seq > p.lastReadSeq AND m.authorId != p.userId AND m.isDeleted = 0)
                   AS unread
           FROM participants p JOIN conversations c ON c.id = p.conversationId
           WHERE p.userId = ? AND p.conversationId = ?""",
        userId,
        conversationId,
    ) { row ->
        val lastSeq = row.getLong("lastSeq")
        ConversationDto(
            id = row.getString("id"),
            title = if (row.getBoolean("isGroup")) row.getString("title") else row.getString("otherName"),
            avatarUrl = null,
            sortKey = row.getLong("sortKey"),
            version = row.getLong("version"),
            unreadCount = row.getInt("unread"),
            lastSeq = lastSeq,
            lastReadSeq = row.getLong("lastReadSeq"),
            lastMessage = null,
        )
    }?.let { it.copy(lastMessage = if (it.lastSeq == 0L) null else message("${it.id}:${it.lastSeq}")) }

    private fun Transaction.message(id: String): MessageDto? = messagesWhere("m.id = ?", id).firstOrNull()

    private fun Transaction.messagesWhere(
        condition: String,
        vararg args: Any?,
    ): List<MessageDto> = query(
        "SELECT m.*, u.name AS authorName FROM messages m JOIN users u ON u.id = m.authorId WHERE $condition",
        *args,
    ) { it.toMessage() }

    private fun ResultSet.toMessage() = MessageDto(
        id = getString("id"),
        conversationId = getString("conversationId"),
        seq = getLong("seq"),
        version = getLong("version"),
        authorId = getString("authorId"),
        authorName = getString("authorName"),
        text = getString("text"),
        sentAtMillis = getLong("sentAtMillis"),
        editedAtMillis = getLongOrNull("editedAtMillis"),
        isDeleted = getBoolean("isDeleted"),
    )

    private companion object {
        const val JOURNAL_LIMIT = 5_000
    }
}

// Which page of a chat: the newest, or next to a seq.
sealed interface MessagesPage {
    val limit: Int

    data class Latest(override val limit: Int) : MessagesPage

    data class Before(
        val seq: Long,
        override val limit: Int,
    ) : MessagesPage

    data class After(
        val seq: Long,
        override val limit: Int,
    ) : MessagesPage

    data class Around(
        val seq: Long,
        override val limit: Int,
    ) : MessagesPage
}
