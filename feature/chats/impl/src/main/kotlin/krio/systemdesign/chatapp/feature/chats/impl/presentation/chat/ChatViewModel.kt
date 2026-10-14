package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import krio.systemdesign.chatapp.core.composeutils.state.savedTextField
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LoadNewerMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LoadOlderMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.MarkConversationReadUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveConversationUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveOutgoingMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.OpenAroundMessageUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.OpenLatestMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.RefreshLatestMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.RetryMessageUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.SendMessageUseCase
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

// Holds the screen's Store, so it outlives a rotation. chatId comes from the screen's navigation key:
// koinViewModel { parametersOf(chatId) }. The use cases only pass through to the Executor and its paginator.
@KoinViewModel
internal class ChatViewModel(
    @InjectedParam chatId: String,
    savedStateHandle: SavedStateHandle,
    storeFactory: StoreFactory,
    observeMessages: ObserveMessagesUseCase,
    observeConversation: ObserveConversationUseCase,
    openLatestMessages: OpenLatestMessagesUseCase,
    refreshLatestMessages: RefreshLatestMessagesUseCase,
    openAroundMessage: OpenAroundMessageUseCase,
    loadOlderMessages: LoadOlderMessagesUseCase,
    loadNewerMessages: LoadNewerMessagesUseCase,
    observeOutgoing: ObserveOutgoingMessagesUseCase,
    markRead: MarkConversationReadUseCase,
    sendMessage: SendMessageUseCase,
    retryMessage: RetryMessageUseCase,
) : ViewModel() {

    private val store = storeFactory.create(
        name = "Chat",
        // The typed text survives process death.
        initialState = ChatState(input = savedStateHandle.savedTextField(INPUT_KEY)),
        reducer = ChatReducer,
        executor = ChatExecutor(
            conversationId = chatId,
            paginator = ChatPaginator(
                conversationId = chatId,
                observeMessages = observeMessages,
                openLatestMessages = openLatestMessages,
                refreshLatestMessages = refreshLatestMessages,
                openAroundMessage = openAroundMessage,
                loadOlderMessages = loadOlderMessages,
                loadNewerMessages = loadNewerMessages,
            ),
            observeConversation = observeConversation,
            observeOutgoing = observeOutgoing,
            markRead = markRead,
            sendMessage = sendMessage,
            retryMessage = retryMessage,
        ),
        scope = viewModelScope,
    )

    val state: StateFlow<ChatState> = store.state

    val effects: Flow<ChatEffect> = store.effects

    fun onIntent(intent: ChatIntent) = store.accept(intent)

    private companion object {
        const val INPUT_KEY = "input"
    }
}
