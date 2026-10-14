package krio.systemdesign.chatapp.contract

import kotlinx.serialization.Serializable

// seq numbers a chat's messages 1, 2, 3… with no gaps: a deleted message stays as a tombstone (isDeleted),
// so a gap in a client's cache is a part it hasn't loaded. version grows with every change (edit, delete).
@Serializable
data class MessageDto(
    val id: String,
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

// Newest first.
@Serializable
data class MessagesPageDto(val items: List<MessageDto>)

// A batch of a chat's edits and deletions after a sync token.
@Serializable
data class MessageChangesDto(
    // Edited and deleted (tombstone) messages, as they are now.
    val changed: List<MessageDto>,
    // The token to save once the batch is applied.
    val nextToken: String,
    // More changes are waiting: ask again with nextToken.
    val hasMore: Boolean,
    // The token is too old: the client drops its cache of this chat.
    val resetRequired: Boolean,
)

// clientId is the sender's id for the message: a retry with the same one returns the message sent the first time.
@Serializable
data class SendMessageRequestDto(
    val clientId: String,
    val text: String,
)

@Serializable
data class SendMessageResponseDto(
    val message: MessageDto,
    // The chat as the sender sees it now: its lastSeq and place in the list moved.
    val conversation: ConversationDto,
)
