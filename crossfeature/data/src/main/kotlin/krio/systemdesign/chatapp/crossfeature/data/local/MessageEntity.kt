package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import krio.systemdesign.chatapp.crossfeature.domain.model.Message

// A cached message. (conversationId, seq) is unique: a chat's messages are numbered without gaps on the server,
// so a gap here is a part not loaded yet.
@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "seq"], unique = true)],
)
internal data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val seq: Long,
    val version: Long,
    val authorId: String,
    val authorName: String,
    val text: String,
    val sentAtMillis: Long,
    val editedAtMillis: Long?,
    val isDeleted: Boolean,
)

internal fun MessageEntity.toDomain(myUserId: String?) = Message(
    id = id,
    conversationId = conversationId,
    seq = seq,
    authorId = authorId,
    authorName = authorName,
    isMine = authorId == myUserId,
    text = text,
    sentAtMillis = sentAtMillis,
    editedAtMillis = editedAtMillis,
    isDeleted = isDeleted,
)
