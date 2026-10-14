package krio.systemdesign.chatapp.crossfeature.domain.model

// seq numbers a chat's messages 1, 2, 3… with no gaps; a deleted one stays as a tombstone (isDeleted).
data class Message(
    val id: String,
    val conversationId: String,
    val seq: Long,
    val authorId: String,
    val authorName: String,
    // Written by the signed-in user.
    val isMine: Boolean,
    val text: String,
    val sentAtMillis: Long,
    val editedAtMillis: Long?,
    val isDeleted: Boolean,
)
