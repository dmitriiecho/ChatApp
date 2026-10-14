package krio.systemdesign.chatapp.contract

import kotlinx.serialization.Serializable

// A chat as one user sees it. sortKey is unique for the user and only grows (a new message raises it), so chats
// only jump up the list. version grows with every change. lastSeq and lastReadSeq are 0 with no messages / none read.
@Serializable
data class ConversationDto(
    val id: String,
    val title: String,
    val avatarUrl: String?,
    val sortKey: Long,
    val version: Long,
    val unreadCount: Int,
    val lastSeq: Long,
    val lastReadSeq: Long,
    val lastMessage: MessageDto?,
)

// Most active first.
@Serializable
data class ConversationsPageDto(
    val items: List<ConversationDto>,
    val hasMore: Boolean,
)

// A batch of the user's chat list changes after a sync token.
@Serializable
data class ConversationChangesDto(
    // Changed chats, as they are now.
    val changed: List<ConversationDto>,
    // Chats the user is no longer in.
    val removedIds: List<String>,
    // The token to save once the batch is applied.
    val nextToken: String,
    // More changes are waiting: ask again with nextToken.
    val hasMore: Boolean,
    // The token is too old: the client starts its list again from the first page.
    val resetRequired: Boolean,
)

// Marks the chat read up to seq. A smaller seq than the one already read changes nothing.
@Serializable
data class ReadRequestDto(val seq: Long)
