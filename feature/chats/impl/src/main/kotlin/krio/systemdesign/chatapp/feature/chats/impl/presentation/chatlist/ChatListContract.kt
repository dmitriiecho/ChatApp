package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import krio.systemdesign.chatapp.core.mvi.StoreAccess
import krio.systemdesign.chatapp.crossfeature.domain.model.ConversationsSlice

internal data class ChatListState(
    val chats: Chats = Chats.Loading,
    // Chats above the window: the user scrolled far down and the top left the window.
    val hasAbove: Boolean = false,
    // Chats below the window, in the cache or on the server.
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    // Who is signed in, for the top bar.
    val userName: String? = null,
    val bots: Bots = Bots.Unknown,
) {
    sealed interface Bots {
        // Until the app reaches the server: the switch is off and can't be used.
        data object Unknown : Bots

        // requested: the user's choice while the server hasn't answered yet; the switch shows it.
        data class Known(
            val isEnabled: Boolean,
            val requested: Boolean? = null,
        ) : Bots {
            val isOn: Boolean get() = requested ?: isEnabled
        }
    }

    sealed interface Chats {
        // Until the list's first sync: an empty list then means "not loaded yet", not "no chats".
        data object Loading : Chats

        data class Loaded(val items: List<Item>) : Chats
    }

    data class Item(
        val id: String,
        val title: String,
        // Null in a chat with no messages yet.
        val lastMessage: LastMessage?,
        val unreadCount: Int,
    )

    data class LastMessage(
        val text: String,
        val isMine: Boolean,
        val isDeleted: Boolean,
        val sentAtMillis: Long,
    )
}

internal sealed interface ChatListIntent {
    data class ChatClicked(val chatId: String) : ChatListIntent

    // The list scrolled near its top or bottom edge.
    data object ReachedTop : ChatListIntent

    data object ReachedBottom : ChatListIntent

    data object LogoutClicked : ChatListIntent

    data class BotsToggled(val enabled: Boolean) : ChatListIntent
}

internal sealed interface ChatListMutation {
    data class SliceChanged(
        val slice: ConversationsSlice,
        val hasAbove: Boolean,
    ) : ChatListMutation

    data class LoadingMoreChanged(val isLoading: Boolean) : ChatListMutation

    data class UserChanged(val name: String?) : ChatListMutation

    // Null: the app hasn't reached the server.
    data class BotsChanged(val enabled: Boolean?) : ChatListMutation

    // Null: the request is over.
    data class BotsRequested(val enabled: Boolean?) : ChatListMutation
}

internal sealed interface ChatListEffect {
    data class OpenChat(val chatId: String) : ChatListEffect

    // More chats couldn't be loaded: no network.
    data object ShowError : ChatListEffect

    // The bots couldn't be switched: no network.
    data object ShowBotsError : ChatListEffect
}

// The Store as the Executor sees it; the full type is long.
internal typealias ChatListStore = StoreAccess<ChatListState, ChatListMutation, ChatListEffect>
