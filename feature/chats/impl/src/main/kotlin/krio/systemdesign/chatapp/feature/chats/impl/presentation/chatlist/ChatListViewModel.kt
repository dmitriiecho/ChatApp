package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.EnsureConversationsLoadedUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LogoutUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveBotsUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveConversationsUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveSessionUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.SetBotsEnabledUseCase
import org.koin.core.annotation.KoinViewModel

// Holds the screen's Store, so it outlives a rotation.
@KoinViewModel
internal class ChatListViewModel(
    storeFactory: StoreFactory,
    observeConversations: ObserveConversationsUseCase,
    ensureConversationsLoaded: EnsureConversationsLoadedUseCase,
    observeSession: ObserveSessionUseCase,
    logout: LogoutUseCase,
    observeBots: ObserveBotsUseCase,
    setBotsEnabled: SetBotsEnabledUseCase,
) : ViewModel() {

    private val store = storeFactory.create(
        name = "ChatList",
        initialState = ChatListState(),
        reducer = ChatListReducer,
        executor = ChatListExecutor(
            paginator = ConversationsPaginator(observeConversations, ensureConversationsLoaded),
            observeSession = observeSession,
            logout = logout,
            observeBots = observeBots,
            setBotsEnabled = setBotsEnabled,
        ),
        scope = viewModelScope,
    )

    val state: StateFlow<ChatListState> = store.state

    val effects: Flow<ChatListEffect> = store.effects

    fun onIntent(intent: ChatListIntent) = store.accept(intent)
}
