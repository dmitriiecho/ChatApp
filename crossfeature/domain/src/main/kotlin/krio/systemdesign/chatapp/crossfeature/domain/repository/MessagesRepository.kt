package krio.systemdesign.chatapp.crossfeature.domain.repository

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.Window

// One chat's messages. Reads come from the cache only; the network writes into it. Calls that need the network
// throw IOException when it fails.
interface MessagesRepository {

    // The window's messages, newest first, updated as the cache changes. Never with a gap.
    fun observe(
        conversationId: String,
        window: Window,
    ): Flow<List<Message>>

    // The chat itself: its lastSeq and lastReadSeq. Null while it isn't cached.
    fun observeConversation(conversationId: String): Flow<Conversation?>

    // A window on the newest cached messages, without the network, so a chat opens at once. Null: nothing cached.
    suspend fun openLatest(conversationId: String): Window?

    // From the network: the chat, its edits and its newest page, all into the cache.
    suspend fun refreshLatest(conversationId: String)

    // A window around seq, loading the page around it when it isn't cached. Null: no such message.
    suspend fun openAround(
        conversationId: String,
        seq: Long,
    ): Window?

    // Grows the window up from beforeSeq; returns the new fromSeq.
    suspend fun loadOlder(
        conversationId: String,
        beforeSeq: Long,
    ): Long

    // Grows the window down from afterSeq; returns the new toSeq, null when it reached the chat's end.
    suspend fun loadNewer(
        conversationId: String,
        afterSeq: Long,
    ): Long?

    // Read up to seq, here and on the user's other devices.
    suspend fun markRead(
        conversationId: String,
        seq: Long,
    )
}
