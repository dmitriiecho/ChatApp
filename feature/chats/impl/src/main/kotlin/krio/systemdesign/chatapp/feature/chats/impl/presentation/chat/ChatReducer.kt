package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import krio.systemdesign.chatapp.core.mvi.Reducer

internal object ChatReducer : Reducer<ChatState, ChatMutation> {

    override fun reduce(
        state: ChatState,
        mutation: ChatMutation,
    ): ChatState = when (mutation) {
        is ChatMutation.ConversationChanged -> state.copy(
            title = mutation.conversation?.title,
            isEmpty = mutation.conversation?.lastSeq == 0L,
        )
        is ChatMutation.PageChanged -> state.copy(
            messages = mutation.page.messages,
            hasOlder = mutation.page.hasOlder,
            hasNewer = mutation.page.hasNewer,
        )
        is ChatMutation.OutgoingChanged -> state.copy(outgoing = mutation.outgoing)
        is ChatMutation.LoadingOlderChanged -> state.copy(isLoadingOlder = mutation.isLoading)
        is ChatMutation.LoadingNewerChanged -> state.copy(isLoadingNewer = mutation.isLoading)
        is ChatMutation.DividerSet -> state.copy(unreadDividerSeq = mutation.seq)
    }
}
