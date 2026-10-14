package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import krio.systemdesign.chatapp.feature.chats.impl.CHAT
import krio.systemdesign.chatapp.feature.chats.impl.TestMessagesRepository
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LoadNewerMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LoadOlderMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.OpenAroundMessageUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.OpenLatestMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.RefreshLatestMessagesUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ChatPaginatorTest {

    private val messages = TestMessagesRepository(lastSeq = 3_000)
    private val paginator = ChatPaginator(
        conversationId = CHAT,
        observeMessages = ObserveMessagesUseCase(messages),
        openLatestMessages = OpenLatestMessagesUseCase(messages),
        refreshLatestMessages = RefreshLatestMessagesUseCase(messages),
        openAroundMessage = OpenAroundMessageUseCase(messages),
        loadOlderMessages = LoadOlderMessagesUseCase(messages),
        loadNewerMessages = LoadNewerMessagesUseCase(messages),
    )

    // A jump to the latest, and before its messages are read, a load at the bottom asked for by the old list: the
    // load must not pair the new window with the old list's messages.
    @Test
    fun `a load right after a jump doesn't empty the window`() = runTest {
        val page = MutableStateFlow<ChatPaginator.Page?>(null)
        backgroundScope.launch { paginator.observe(MutableStateFlow(3_000L)) {}.collect { page.value = it } }
        paginator.openLatest()
        runCurrent()
        repeat(20) {
            paginator.loadOlder()
            runCurrent()
        }

        paginator.openLatest()
        paginator.loadNewer()
        runCurrent()

        assertThat(page.value!!.messages).isNotEmpty()
        assertThat(page.value!!.messages.first().seq).isEqualTo(3_000)
    }
}
