package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import krio.systemdesign.chatapp.core.mvi.Executor
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LogoutUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveBotsUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveSessionUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.SetBotsEnabledUseCase

// The chat list doesn't sync: the app does it on every connection. The screen only moves its window.
internal class ChatListExecutor(
    private val paginator: ConversationsPaginator,
    private val observeSession: ObserveSessionUseCase,
    private val logout: LogoutUseCase,
    private val observeBots: ObserveBotsUseCase,
    private val setBotsEnabled: SetBotsEnabledUseCase,
) : Executor<ChatListIntent, ChatListState, ChatListMutation, ChatListEffect> {

    override suspend fun whileSubscribed(store: ChatListStore) = coroutineScope {
        launch {
            paginator.observe().collect { (window, slice) ->
                store.dispatch(ChatListMutation.SliceChanged(slice, hasAbove = window.offset > 0))
            }
        }
        launch { observeBots().collect { store.dispatch(ChatListMutation.BotsChanged(it)) } }
        observeSession().collect { store.dispatch(ChatListMutation.UserChanged(it?.userName)) }
    }

    override suspend fun execute(
        intent: ChatListIntent,
        store: ChatListStore,
    ) {
        when (intent) {
            is ChatListIntent.ChatClicked -> store.publish(ChatListEffect.OpenChat(intent.chatId))
            ChatListIntent.ReachedBottom -> if (store.state.hasMore && !store.state.isLoadingMore) loadBelow(store)
            ChatListIntent.ReachedTop -> if (store.state.hasAbove) paginator.loadAbove()
            // The app follows the session and opens the sign-in.
            ChatListIntent.LogoutClicked -> logout()
            is ChatListIntent.BotsToggled -> toggleBots(intent.enabled, store)
        }
    }

    // One request at a time; the switch shows the choice until the server answers.
    private suspend fun toggleBots(
        enabled: Boolean,
        store: ChatListStore,
    ) {
        val bots = store.state.bots as? ChatListState.Bots.Known ?: return
        if (bots.requested != null) return
        store.dispatch(ChatListMutation.BotsRequested(enabled))
        try {
            // The answer comes here first: the observed value may follow a moment later.
            store.dispatch(ChatListMutation.BotsChanged(setBotsEnabled(enabled)))
        } catch (e: IOException) {
            store.publish(ChatListEffect.ShowBotsError)
        } finally {
            store.dispatch(ChatListMutation.BotsRequested(null))
        }
    }

    private suspend fun loadBelow(store: ChatListStore) {
        store.dispatch(ChatListMutation.LoadingMoreChanged(true))
        try {
            paginator.loadBelow()
        } catch (e: IOException) {
            store.publish(ChatListEffect.ShowError)
        } finally {
            store.dispatch(ChatListMutation.LoadingMoreChanged(false))
        }
    }
}
