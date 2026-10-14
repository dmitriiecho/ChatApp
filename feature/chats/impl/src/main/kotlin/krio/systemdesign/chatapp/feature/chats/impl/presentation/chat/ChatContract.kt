package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import androidx.compose.foundation.text.input.TextFieldState
import krio.systemdesign.chatapp.core.mvi.StoreAccess
import krio.systemdesign.chatapp.crossfeature.domain.model.Conversation
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage

// input is the one part that changes outside the Reducer: the field edits its TextFieldState as the user types,
// as Compose recommends, instead of a Mutation per keystroke. The State keeps the same instance throughout.
internal data class ChatState(
    val input: TextFieldState,
    // Null until the chat is cached.
    val title: String? = null,
    // The window's messages, newest first.
    val messages: List<Message> = emptyList(),
    // Not on the server yet, oldest first; shown under the newest messages.
    val outgoing: List<OutgoingMessage> = emptyList(),
    val hasOlder: Boolean = false,
    // Messages newer than the window: also whether to show the "to the latest" button.
    val hasNewer: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isLoadingNewer: Boolean = false,
    // The first message unread when the chat opened: the "new messages" divider goes above it.
    val unreadDividerSeq: Long? = null,
    // The chat has no messages at all.
    val isEmpty: Boolean = false,
) {
    val canSend: Boolean
        get() = input.text.isNotBlank()
}

internal sealed interface ChatIntent {
    // The list scrolled near its top (older) or bottom (newer) edge.
    data object ReachedTop : ChatIntent

    data object ReachedBottom : ChatIntent

    data object JumpToLatest : ChatIntent

    // To a random message of the history, to see the window load both ways.
    data object JumpSomewhereClicked : ChatIntent

    // The newest message on screen: the chat is read up to it.
    data class MessagesSeen(val seq: Long) : ChatIntent

    data object SendClicked : ChatIntent

    data class RetryClicked(val clientId: String) : ChatIntent

    data object BackClicked : ChatIntent
}

internal sealed interface ChatMutation {
    data class ConversationChanged(val conversation: Conversation?) : ChatMutation

    data class PageChanged(val page: ChatPaginator.Page) : ChatMutation

    data class OutgoingChanged(val outgoing: List<OutgoingMessage>) : ChatMutation

    data class LoadingOlderChanged(val isLoading: Boolean) : ChatMutation

    data class LoadingNewerChanged(val isLoading: Boolean) : ChatMutation

    data class DividerSet(val seq: Long) : ChatMutation
}

internal sealed interface ChatEffect {
    data object NavigateBack : ChatEffect

    data object ScrollToBottom : ChatEffect

    // The window opened around a message (the first unread, a jump): scroll to it once it is on the list.
    data class ScrollToSeq(val seq: Long) : ChatEffect

    // New messages came at the bottom: scroll to them if the user is at the bottom.
    data object NewMessagesArrived : ChatEffect

    // Something couldn't load: no network.
    data object ShowError : ChatEffect
}

// The Store as the Executor sees it; the full type is long.
internal typealias ChatStore = StoreAccess<ChatState, ChatMutation, ChatEffect>
