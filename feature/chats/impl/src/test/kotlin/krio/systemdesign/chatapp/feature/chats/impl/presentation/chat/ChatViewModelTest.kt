package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import krio.systemdesign.chatapp.core.composeutils.keepCollecting
import krio.systemdesign.chatapp.core.composeutils.typeText
import krio.systemdesign.chatapp.core.composeutils.viewModelTest
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.feature.chats.impl.CHAT
import krio.systemdesign.chatapp.feature.chats.impl.TestMessagesRepository
import krio.systemdesign.chatapp.feature.chats.impl.TestOutboxRepository
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

class ChatViewModelTest {

    private val outbox = TestOutboxRepository()

    private fun viewModel(messages: TestMessagesRepository) = ChatViewModel(
        chatId = CHAT,
        savedStateHandle = SavedStateHandle(),
        storeFactory = StoreFactory(),
        observeMessages = ObserveMessagesUseCase(messages),
        observeConversation = ObserveConversationUseCase(messages),
        openLatestMessages = OpenLatestMessagesUseCase(messages),
        refreshLatestMessages = RefreshLatestMessagesUseCase(messages),
        openAroundMessage = OpenAroundMessageUseCase(messages),
        loadOlderMessages = LoadOlderMessagesUseCase(messages),
        loadNewerMessages = LoadNewerMessagesUseCase(messages),
        observeOutgoing = ObserveOutgoingMessagesUseCase(outbox),
        markRead = MarkConversationReadUseCase(messages),
        sendMessage = SendMessageUseCase(outbox),
        retryMessage = RetryMessageUseCase(outbox),
    )

    private fun TestScope.opened(messages: TestMessagesRepository): ChatViewModel {
        val viewModel = viewModel(messages)
        keepCollecting(viewModel.state)
        return viewModel
    }

    private val ChatViewModel.seqs get() = state.value.messages.map { it.seq }

    @Test
    fun `a read chat opens at its newest cached messages and refreshes`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 100)

        val viewModel = opened(messages)

        assertThat(viewModel.seqs.first()).isEqualTo(100)
        assertThat(viewModel.seqs.last()).isEqualTo(51)
        assertThat(viewModel.state.value.hasNewer).isFalse()
        assertThat(viewModel.state.value.unreadDividerSeq).isNull()
        assertThat(messages.refreshes).isEqualTo(1)
    }

    @Test
    fun `a chat with unread messages opens at the first one, with the divider`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 1_000, unreadCount = 200)

        viewModel(messages).let { viewModel ->
            viewModel.effects.test {
                keepCollecting(viewModel.state)

                assertThat(awaitItem()).isEqualTo(ChatEffect.ScrollToSeq(801))
            }
            assertThat(viewModel.state.value.unreadDividerSeq).isEqualTo(801)
            assertThat(viewModel.seqs).contains(801)
            // Opened in the middle: the refresh doesn't move it down.
            assertThat(viewModel.state.value.hasNewer).isTrue()
        }
    }

    @Test
    fun `offline the cache stays and a snackbar says why`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 10).apply { isOffline = true }

        viewModel(messages).let { viewModel ->
            viewModel.effects.test {
                keepCollecting(viewModel.state)

                assertThat(awaitItem()).isEqualTo(ChatEffect.ShowError)
            }
            assertThat(viewModel.seqs.size).isEqualTo(10)
        }
    }

    @Test
    fun `scrolling up stops the head and the window keeps 300 messages`() = viewModelTest {
        val viewModel = opened(TestMessagesRepository(lastSeq = 1_000))

        repeat(6) { viewModel.onIntent(ChatIntent.ReachedTop) }

        // 951–1000, then six pages up: from 651, cut to 300 at the bottom.
        assertThat(viewModel.seqs.first()).isEqualTo(950)
        assertThat(viewModel.seqs.last()).isEqualTo(651)
        assertThat(viewModel.state.value.hasNewer).isTrue()
    }

    @Test
    fun `back at the end, the window is the head again and new messages are announced`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 1_000)
        val viewModel = opened(messages)
        repeat(6) { viewModel.onIntent(ChatIntent.ReachedTop) }

        viewModel.effects.test {
            repeat(2) { viewModel.onIntent(ChatIntent.ReachedBottom) }

            assertThat(awaitItem()).isEqualTo(ChatEffect.NewMessagesArrived)
        }
        assertThat(viewModel.seqs.first()).isEqualTo(1_000)
        assertThat(viewModel.state.value.hasNewer).isFalse()
    }

    @Test
    fun `a message arriving at the bottom is announced`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 10)
        val viewModel = opened(messages)

        viewModel.effects.test {
            messages.receive("Hello")

            assertThat(awaitItem()).isEqualTo(ChatEffect.NewMessagesArrived)
        }
        assertThat(viewModel.seqs.first()).isEqualTo(11)
    }

    @Test
    fun `send puts the trimmed text into the outbox and clears the field`() = viewModelTest {
        val viewModel = opened(TestMessagesRepository(lastSeq = 10))
        viewModel.state.value.input.typeText("  See you  ")

        viewModel.onIntent(ChatIntent.SendClicked)

        assertThat(outbox.outgoing.value.map { it.text }).containsExactly("See you")
        assertThat(viewModel.state.value.input.text.toString()).isEmpty()
        assertThat(viewModel.state.value.outgoing.size).isEqualTo(1)
    }

    @Test
    fun `a blank message can't be sent`() = viewModelTest {
        val viewModel = opened(TestMessagesRepository(lastSeq = 10))
        viewModel.state.value.input.typeText("   ")

        viewModel.onIntent(ChatIntent.SendClicked)

        assertThat(viewModel.state.value.canSend).isFalse()
        assertThat(outbox.outgoing.value).isEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the chat is read up to the newest message seen, once a second`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 10, unreadCount = 5)
        val viewModel = opened(messages)

        viewModel.onIntent(ChatIntent.MessagesSeen(7))
        viewModel.onIntent(ChatIntent.MessagesSeen(9))
        advanceTimeBy(1_001)
        runCurrent()

        assertThat(messages.reads).containsExactly(9L)
    }

    @Test
    fun `a jump opens the window around a message of the history and scrolls to it`() = viewModelTest {
        val messages = TestMessagesRepository(lastSeq = 3_000)
        val viewModel = opened(messages)

        viewModel.effects.test {
            viewModel.onIntent(ChatIntent.JumpSomewhereClicked)

            val seq = (awaitItem() as ChatEffect.ScrollToSeq).seq
            assertThat(seq in 1L..3_000L).isTrue()
            assertThat(seq in viewModel.seqs).isTrue()
        }
    }

    @Test
    fun `back closes the chat`() = viewModelTest {
        val viewModel = viewModel(TestMessagesRepository(lastSeq = 1))

        viewModel.effects.test {
            viewModel.onIntent(ChatIntent.BackClicked)

            assertThat(awaitItem()).isEqualTo(ChatEffect.NavigateBack)
        }
    }

    private fun assertk.Assert<List<Long>>.contains(seq: Long) = given { assertThat(seq in it).isTrue() }
}
