package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import krio.systemdesign.chatapp.crossfeature.domain.model.ConversationsSlice
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.EnsureConversationsLoadedUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveConversationsUseCase

// The window of the chat list the screen watches: two numbers, from which chat (offset, 0 the most active) and
// how many (count). It grows down to MAX_WINDOW_SIZE and then moves:
//   open the screen        (0, 50)    chats 1–50
//   scroll down            (0, 100)…(0, 150)
//   scroll further down    (50, 150)  chats 51–200: the top leaves the subscription
//   scroll back up         (0, 150)   all above is cached already, so no network
// Room re-reads the window on every change, so it never holds more than MAX_WINDOW_SIZE chats however far the
// user scrolls; what leaves it stays in Room and comes back at once.
// Re-sorting doesn't break the window: chats only jump up. Compose keeps the visible rows in place by key.
// Called on the main thread (viewModelScope), so its checks of the window hold.
internal class ConversationsPaginator(
    private val observeConversations: ObserveConversationsUseCase,
    private val ensureConversationsLoaded: EnsureConversationsLoadedUseCase,
) {
    data class ListWindow(
        val offset: Int,
        val count: Int,
    )

    private val window = MutableStateFlow(ListWindow(offset = 0, count = PAGE_SIZE))

    // The window with the slice read for it, so a slice of the old window never pairs with a new one.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<Pair<ListWindow, ConversationsSlice>> = window
        .flatMapLatest { w -> observeConversations(w.offset, w.count).map { slice -> w to slice } }
        .onEach { (w, slice) ->
            // The list started over (resetRequired) or chats went away while the window was deep down: it points
            // where there are no chats now. Back to the top.
            if (w.offset > 0 && slice.items.isEmpty()) window.compareAndSet(w, ListWindow(0, PAGE_SIZE))
        }

    // The window grows down up to MAX_WINDOW_SIZE and then moves. The chats it moves onto are loaded first:
    // offline that fails with IOException and the window stays as it was, nothing to undo.
    suspend fun loadBelow() {
        val current = window.value
        val next = if (current.count + PAGE_SIZE <= MAX_WINDOW_SIZE) {
            current.copy(count = current.count + PAGE_SIZE)
        } else {
            current.copy(offset = current.offset + PAGE_SIZE)
        }
        ensureConversationsLoaded(count = next.offset + next.count)
        window.compareAndSet(current, next)
    }

    // Everything above is cached: no network.
    fun loadAbove() {
        window.update { w -> if (w.offset == 0) w else w.copy(offset = maxOf(0, w.offset - PAGE_SIZE)) }
    }

    companion object {
        const val PAGE_SIZE = 50

        // The most chats in the window, and so in every re-read of it.
        const val MAX_WINDOW_SIZE = 150
    }
}
