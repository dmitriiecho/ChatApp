package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ChatListReducerTest {

    private fun ChatListState.reduce(vararg mutations: ChatListMutation) =
        mutations.fold(this) { state, mutation -> ChatListReducer.reduce(state, mutation) }

    @Test
    fun `while the server hasn't answered, the switch shows the user's choice`() {
        val state = ChatListState().reduce(
            ChatListMutation.BotsChanged(true),
            ChatListMutation.BotsRequested(false),
            // Another device's change arriving meanwhile.
            ChatListMutation.BotsChanged(true),
        )

        assertThat((state.bots as ChatListState.Bots.Known).isOn).isEqualTo(false)
    }

    @Test
    fun `once the request is over, the switch shows the server's value`() {
        val state = ChatListState().reduce(
            ChatListMutation.BotsChanged(true),
            ChatListMutation.BotsRequested(false),
            ChatListMutation.BotsRequested(null),
        )

        assertThat(state.bots).isEqualTo(ChatListState.Bots.Known(isEnabled = true))
    }

    @Test
    fun `losing the server makes the switch unknown`() {
        val state = ChatListState().reduce(ChatListMutation.BotsChanged(true), ChatListMutation.BotsChanged(null))

        assertThat(state.bots).isEqualTo(ChatListState.Bots.Unknown)
    }
}
