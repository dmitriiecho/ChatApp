package krio.systemdesign.chatapp.crossfeature.domain.repository

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.model.ConversationsSlice

// The chat list. How many chats are loaded into the cache is the data layer's business; which part of the list a
// screen shows (offset, count) is the screen's. The cache may hold thousands, a screen watches only its part.
interface ConversationsRepository {

    // count chats from position offset (0: the most active), re-sent on every change: chats re-sort by themselves.
    // Only from the part loaded without gaps from the top.
    fun observeConversations(
        offset: Int,
        count: Int,
    ): Flow<ConversationsSlice>

    // At least count chats in the cache from the top, loading pages from the server when there are fewer.
    // The chats come through observeConversations. IOException without a network.
    suspend fun ensureLoaded(count: Int)
}
