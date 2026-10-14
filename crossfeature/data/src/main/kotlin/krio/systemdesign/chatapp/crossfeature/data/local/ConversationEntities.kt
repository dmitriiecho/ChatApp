package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation
import krio.systemdesign.chatapp.crossfeature.domain.model.Message

// A cached chat as the server sent it, the whole row replaced on every update. sortKey orders the list and
// bounds its paging, version keeps older data from overwriting newer (upsertNewer); neither reaches the screens.
@Entity(tableName = "conversations", indices = [Index("sortKey")])
internal data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val avatarUrl: String?,
    val sortKey: Long,
    val version: Long,
    val unreadCount: Int,
    val lastSeq: Long,
    val lastReadSeq: Long,
    @Embedded(prefix = "last_") val lastMessage: LastMessageEmbedded?,
)

// The last message as columns of the chat's row. Not in messages: there it would be a lone piece of history, and
// offline the chat would open on that one message. Its conversationId is the chat's id.
internal data class LastMessageEmbedded(
    val id: String,
    val seq: Long,
    val authorId: String,
    val authorName: String,
    val text: String,
    val sentAtMillis: Long,
    val editedAtMillis: Long?,
    val isDeleted: Boolean,
)

// The app's notes on the whole chat list, one row (id = 0), kept across restarts.
// loadedDownToSortKey: every chat from this sortKey up is cached; below it the cache has gaps, so the list shows
// nothing below. Long.MIN_VALUE: everything is loaded. hasMore: the server has chats below it.
// syncToken: the chat list log's token, "every change up to here is applied".
// Written by ConversationsRepositoryImpl (paging down) and ConversationsSync (first page, token).
@Entity(tableName = "conversation_list_meta")
internal data class ConversationListMetaEntity(
    @PrimaryKey val id: Int = 0,
    val loadedDownToSortKey: Long,
    val hasMore: Boolean,
    val syncToken: String?,
)

internal fun ConversationEntity.toDomain(myUserId: String?) = Conversation(
    id = id,
    title = title,
    avatarUrl = avatarUrl,
    unreadCount = unreadCount,
    lastSeq = lastSeq,
    lastReadSeq = lastReadSeq,
    lastMessage = lastMessage?.let {
        Message(
            id = it.id,
            conversationId = id,
            seq = it.seq,
            authorId = it.authorId,
            authorName = it.authorName,
            isMine = it.authorId == myUserId,
            text = it.text,
            sentAtMillis = it.sentAtMillis,
            editedAtMillis = it.editedAtMillis,
            isDeleted = it.isDeleted,
        )
    },
)
