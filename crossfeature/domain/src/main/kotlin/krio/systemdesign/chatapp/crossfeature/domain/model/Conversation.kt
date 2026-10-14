package krio.systemdesign.chatapp.crossfeature.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val avatarUrl: String?,
    val unreadCount: Int,
    // The chat's newest seq: newer than the window's newest message means there is more below.
    val lastSeq: Long,
    // For the chat: where the "new messages" divider goes and where it opens.
    val lastReadSeq: Long,
    // Null in a chat with no messages yet.
    val lastMessage: Message?,
)

// A part of the chat list and whether anything is below it, in the cache or still on the server.
// isSynced: the list was loaded once; before that, an empty list means "loading", not "no chats".
data class ConversationsSlice(
    val items: List<Conversation>,
    val hasMore: Boolean,
    val isSynced: Boolean,
)
