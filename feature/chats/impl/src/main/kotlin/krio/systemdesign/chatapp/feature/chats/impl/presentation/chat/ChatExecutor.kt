package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import androidx.compose.foundation.text.input.clearText
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import krio.systemdesign.chatapp.core.mvi.Executor
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.MarkConversationReadUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveConversationUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveOutgoingMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.RetryMessageUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.SendMessageUseCase

internal class ChatExecutor(
    private val conversationId: String,
    private val paginator: ChatPaginator,
    private val observeConversation: ObserveConversationUseCase,
    private val observeOutgoing: ObserveOutgoingMessagesUseCase,
    private val markRead: MarkConversationReadUseCase,
    private val sendMessage: SendMessageUseCase,
    private val retryMessage: RetryMessageUseCase,
    private val random: Random = Random.Default,
) : Executor<ChatIntent, ChatState, ChatMutation, ChatEffect> {

    // The read mark: what the server has, the newest seen, and whether a mark is on its way.
    private var readSeq = 0L
    private var seenSeq = 0L
    private var isMarkingRead = false

    // Opens at the first unread message, or at the newest cached ones, then gets the newest from the network.
    // Offline the cache stays on screen and a snackbar tells why nothing new comes.
    override suspend fun onStart(store: ChatStore) {
        val conversation = observeConversation(conversationId).first()
        readSeq = conversation?.lastReadSeq ?: 0
        val firstUnread = conversation?.takeIf { it.unreadCount > 0 }?.let { it.lastReadSeq + 1 }
        val isOpenedAtUnread = firstUnread != null && openAt(firstUnread)
        if (isOpenedAtUnread) {
            store.dispatch(ChatMutation.DividerSet(firstUnread))
            store.publish(ChatEffect.ScrollToSeq(firstUnread))
        } else {
            paginator.openLatest()
        }
        refreshLatest(store)
    }

    override suspend fun whileSubscribed(store: ChatStore) = coroutineScope {
        launch {
            observeConversation(conversationId).collect { conversation ->
                // Read on another device: no need to send it again.
                conversation?.let { readSeq = maxOf(readSeq, it.lastReadSeq) }
                store.dispatch(ChatMutation.ConversationChanged(conversation))
            }
        }
        launch { observeOutgoing(conversationId).collect { store.dispatch(ChatMutation.OutgoingChanged(it)) } }
        val lastSeqs = observeConversation(conversationId).map { it?.lastSeq ?: 0 }
        paginator.observe(lastSeqs, onNewMessages = { store.publish(ChatEffect.NewMessagesArrived) })
            .collect { store.dispatch(ChatMutation.PageChanged(it)) }
    }

    override suspend fun execute(
        intent: ChatIntent,
        store: ChatStore,
    ) {
        when (intent) {
            ChatIntent.ReachedTop -> if (store.state.hasOlder && !store.state.isLoadingOlder) loadOlder(store)
            ChatIntent.ReachedBottom -> if (store.state.hasNewer && !store.state.isLoadingNewer) loadNewer(store)
            ChatIntent.JumpToLatest -> jumpToLatest(store)
            ChatIntent.JumpSomewhereClicked -> jumpSomewhere(store)
            is ChatIntent.MessagesSeen -> seen(intent.seq)
            ChatIntent.SendClicked -> send(store)
            is ChatIntent.RetryClicked -> retryMessage(intent.clientId)
            ChatIntent.BackClicked -> store.publish(ChatEffect.NavigateBack)
        }
    }

    private suspend fun openAt(seq: Long): Boolean = try {
        paginator.openAt(seq)
    } catch (e: IOException) {
        false
    }

    private suspend fun refreshLatest(store: ChatStore) {
        try {
            paginator.refreshLatest()
        } catch (e: IOException) {
            store.publish(ChatEffect.ShowError)
        }
    }

    // The cache first and at once, then the network.
    private suspend fun jumpToLatest(store: ChatStore) {
        paginator.openLatest()
        store.publish(ChatEffect.ScrollToBottom)
        refreshLatest(store)
    }

    // Any message from the first to the newest, mostly one not cached yet: the window opens around it with gaps
    // above and below, which load from the network as the user scrolls.
    private suspend fun jumpSomewhere(store: ChatStore) {
        val lastSeq = observeConversation(conversationId).first()?.lastSeq ?: return
        if (lastSeq < 1) return
        val seq = random.nextLong(1, lastSeq + 1)
        try {
            if (paginator.openAt(seq)) store.publish(ChatEffect.ScrollToSeq(seq))
        } catch (e: IOException) {
            store.publish(ChatEffect.ShowError)
        }
    }

    private suspend fun loadOlder(store: ChatStore) {
        store.dispatch(ChatMutation.LoadingOlderChanged(true))
        try {
            paginator.loadOlder()
        } catch (e: IOException) {
            store.publish(ChatEffect.ShowError)
        } finally {
            store.dispatch(ChatMutation.LoadingOlderChanged(false))
        }
    }

    private suspend fun loadNewer(store: ChatStore) {
        store.dispatch(ChatMutation.LoadingNewerChanged(true))
        try {
            if (paginator.loadNewer()) store.publish(ChatEffect.NewMessagesArrived)
        } catch (e: IOException) {
            store.publish(ChatEffect.ShowError)
        } finally {
            store.dispatch(ChatMutation.LoadingNewerChanged(false))
        }
    }

    // At most one mark a second while the user scrolls: the newest seen after the pause goes. Offline it fails
    // quietly; the next message seen tries again.
    private suspend fun seen(seq: Long) {
        seenSeq = maxOf(seenSeq, seq)
        if (isMarkingRead || seenSeq <= readSeq) return
        isMarkingRead = true
        try {
            delay(READ_DELAY_MILLIS)
            val toMark = seenSeq
            markRead(conversationId, toMark)
            readSeq = maxOf(readSeq, toMark)
        } catch (e: IOException) {
            // Not marked: the next message seen tries again.
        } finally {
            isMarkingRead = false
        }
    }

    // The field is cleared first, so the user can type the next message and a second tap finds it empty.
    // From up in the history the chat goes to its end, where the message shows.
    private suspend fun send(store: ChatStore) {
        val state = store.state
        if (!state.canSend) return
        val text = state.input.text.toString()
        state.input.clearText()
        if (state.hasNewer) paginator.openLatest()
        store.publish(ChatEffect.ScrollToBottom)
        sendMessage(conversationId, text)
    }

    private companion object {
        const val READ_DELAY_MILLIS = 1_000L
    }
}
