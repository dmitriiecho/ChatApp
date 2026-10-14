package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test
import krio.systemdesign.chatapp.core.composeutils.keepCollecting
import krio.systemdesign.chatapp.core.composeutils.viewModelTest
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.feature.chats.impl.TestBotsRepository
import krio.systemdesign.chatapp.feature.chats.impl.TestConversationsRepository
import krio.systemdesign.chatapp.feature.chats.impl.TestSessionRepository
import krio.systemdesign.chatapp.feature.chats.impl.conversation
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.EnsureConversationsLoadedUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LogoutUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveBotsUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveConversationsUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveSessionUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.SetBotsEnabledUseCase
import krio.systemdesign.chatapp.feature.chats.impl.message

class ChatListViewModelTest {

    private val chats = TestConversationsRepository((1..400).map { conversation("c$it") })
    private val session = TestSessionRepository()
    private val bots = TestBotsRepository()

    private fun viewModel() = ChatListViewModel(
        StoreFactory(),
        ObserveConversationsUseCase(chats),
        EnsureConversationsLoadedUseCase(chats),
        ObserveSessionUseCase(session),
        LogoutUseCase(session),
        ObserveBotsUseCase(bots),
        SetBotsEnabledUseCase(bots),
    )

    private val ChatListViewModel.ids get() = (state.value.chats as ChatListState.Chats.Loaded).items.map { it.id }

    @Test
    fun `the list is loading until its first sync`() = viewModelTest {
        chats.chats.value = emptyList()
        chats.isSynced.value = false
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        assertThat(viewModel.state.value.chats).isEqualTo(ChatListState.Chats.Loading)

        chats.isSynced.value = true
        assertThat(viewModel.state.value.chats).isEqualTo(ChatListState.Chats.Loaded(emptyList()))
    }

    @Test
    fun `a chat shows its last message, the user's own one marked`() = viewModelTest {
        chats.chats.value =
            listOf(conversation("c", lastMessage = message(3, "On my way", isMine = true), unreadCount = 2))
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        val item = (viewModel.state.value.chats as ChatListState.Chats.Loaded).items.single()
        assertThat(item.lastMessage?.text).isEqualTo("On my way")
        assertThat(item.lastMessage?.isMine).isEqualTo(true)
        assertThat(item.unreadCount).isEqualTo(2)
        assertThat(viewModel.state.value.userName).isEqualTo("Kate")
    }

    @Test
    fun `scrolling down grows the window, then moves it`() = viewModelTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        repeat(3) { viewModel.onIntent(ChatListIntent.ReachedBottom) }

        // 50 → 100 → 150 chats, then the window moves down by 50: chats 51–200.
        assertThat(chats.ensured).containsExactly(100, 150, 200)
        assertThat(viewModel.ids.first()).isEqualTo("c51")
        assertThat(viewModel.ids.size).isEqualTo(150)
        assertThat(viewModel.state.value.hasAbove).isTrue()
    }

    @Test
    fun `scrolling back up moves the window up without the network`() = viewModelTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        repeat(3) { viewModel.onIntent(ChatListIntent.ReachedBottom) }
        chats.isOffline = true

        viewModel.onIntent(ChatListIntent.ReachedTop)

        assertThat(viewModel.ids.first()).isEqualTo("c1")
        assertThat(viewModel.state.value.hasAbove).isFalse()
    }

    @Test
    fun `offline the window stays and a snackbar says why`() = viewModelTest {
        chats.isOffline = true
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.effects.test {
            viewModel.onIntent(ChatListIntent.ReachedBottom)

            assertThat(awaitItem()).isEqualTo(ChatListEffect.ShowError)
        }
        assertThat(viewModel.ids.size).isEqualTo(50)
        assertThat(viewModel.state.value.isLoadingMore).isFalse()
    }

    @Test
    fun `a window pointing where there are no chats now goes back to the top`() = viewModelTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)
        repeat(3) { viewModel.onIntent(ChatListIntent.ReachedBottom) }

        // The list started over with fewer chats.
        chats.chats.value = (1..10).map { conversation("new$it") }

        assertThat(viewModel.ids.first()).isEqualTo("new1")
        assertThat(viewModel.state.value.hasAbove).isFalse()
    }

    @Test
    fun `a click on a chat opens it`() = viewModelTest {
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(ChatListIntent.ChatClicked("c7"))

            assertThat(awaitItem()).isInstanceOf<ChatListEffect.OpenChat>()
        }
    }

    @Test
    fun `signing out goes through the session`() = viewModelTest {
        viewModel().onIntent(ChatListIntent.LogoutClicked)

        assertThat(session.logouts).isEqualTo(1)
    }

    @Test
    fun `the bots switch can't be used until the server is reached`() = viewModelTest {
        bots.isEnabled.value = null
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        assertThat(viewModel.state.value.bots).isEqualTo(ChatListState.Bots.Unknown)

        bots.isEnabled.value = true
        assertThat(viewModel.state.value.bots).isEqualTo(ChatListState.Bots.Known(isEnabled = true))
    }

    @Test
    fun `turning the bots off goes to the server`() = viewModelTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.onIntent(ChatListIntent.BotsToggled(enabled = false))

        assertThat(bots.isEnabled.value).isEqualTo(false)
        assertThat(viewModel.state.value.bots).isEqualTo(ChatListState.Bots.Known(isEnabled = false))
    }

    @Test
    fun `offline the bots switch goes back and a snackbar says why`() = viewModelTest {
        bots.isOffline = true
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        viewModel.effects.test {
            viewModel.onIntent(ChatListIntent.BotsToggled(enabled = false))

            assertThat(awaitItem()).isEqualTo(ChatListEffect.ShowBotsError)
        }
        assertThat(viewModel.state.value.bots).isEqualTo(ChatListState.Bots.Known(isEnabled = true))
    }
}
