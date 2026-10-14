package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage

// A message waiting to be sent, until the server has it. Sent oldest first (createdAtMillis, then clientId).
@Entity(tableName = "outgoing_messages", indices = [Index("conversationId"), Index("createdAtMillis")])
internal data class OutgoingMessageEntity(
    @PrimaryKey val clientId: String,
    val conversationId: String,
    val text: String,
    val createdAtMillis: Long,
    val isFailed: Boolean,
)

internal fun OutgoingMessageEntity.toDomain() = OutgoingMessage(
    clientId = clientId,
    conversationId = conversationId,
    text = text,
    createdAtMillis = createdAtMillis,
    isFailed = isFailed,
)
