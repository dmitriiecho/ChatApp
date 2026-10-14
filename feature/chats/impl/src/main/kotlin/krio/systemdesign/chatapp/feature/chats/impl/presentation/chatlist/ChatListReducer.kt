package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import krio.systemdesign.chatapp.core.mvi.Reducer
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation

internal object ChatListReducer : Reducer<ChatListState, ChatListMutation> {

    override fun reduce(
        state: ChatListState,
        mutation: ChatListMutation,
    ): ChatListState = when (mutation) {
        is ChatListMutation.SliceChanged -> {
            val slice = mutation.slice
            state.copy(
                chats = if (!slice.isSynced && slice.items.isEmpty()) {
                    ChatListState.Chats.Loading
                } else {
                    ChatListState.Chats.Loaded(slice.items.map { it.toItem() })
                },
                hasAbove = mutation.hasAbove,
                hasMore = slice.hasMore,
            )
        }
        is ChatListMutation.LoadingMoreChanged -> state.copy(isLoadingMore = mutation.isLoading)
        is ChatListMutation.UserChanged -> state.copy(userName = mutation.name)
        is ChatListMutation.BotsChanged -> state.copy(
            bots = mutation.enabled?.let { enabled ->
                ChatListState.Bots.Known(enabled, requested = (state.bots as? ChatListState.Bots.Known)?.requested)
            } ?: ChatListState.Bots.Unknown,
        )
        is ChatListMutation.BotsRequested -> when (val bots = state.bots) {
            is ChatListState.Bots.Known -> state.copy(bots = bots.copy(requested = mutation.enabled))
            ChatListState.Bots.Unknown -> state
        }
    }

    private fun Conversation.toItem() = ChatListState.Item(
        id = id,
        title = title,
        lastMessage = lastMessage?.let {
            ChatListState.LastMessage(
                text = it.text,
                isMine = it.isMine,
                isDeleted = it.isDeleted,
                sentAtMillis = it.sentAtMillis,
            )
        },
        unreadCount = unreadCount,
    )
}
