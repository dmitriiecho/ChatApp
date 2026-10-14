package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.Window
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LoadNewerMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.LoadOlderMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.ObserveMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.OpenAroundMessageUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.OpenLatestMessagesUseCase
import krio.systemdesign.chatapp.feature.chats.impl.domain.usecase.RefreshLatestMessagesUseCase

// The chat's window: two seqs, and the messages read for them. The head is a window open at the bottom
// (toSeq == null): new messages join it by themselves. A window is the head only while the user is at the bottom.
// Room re-reads the whole window on every change of messages, so it is kept to MAX_WINDOW_SIZE; the excess is cut
// where the user isn't looking:
//   scrolling up             the window stops being the head, the excess goes from the bottom (toSeq)
//   scrolling down           the excess goes from the top (fromSeq)
//   at the bottom, new ones  the head grows, the excess goes from the top
// What is cut stays in Room and comes back when the user scrolls there.
// Called on the main thread (viewModelScope), so its checks of the window hold.
internal class ChatPaginator(
    private val conversationId: String,
    private val observeMessages: ObserveMessagesUseCase,
    private val openLatestMessages: OpenLatestMessagesUseCase,
    private val refreshLatestMessages: RefreshLatestMessagesUseCase,
    private val openAroundMessage: OpenAroundMessageUseCase,
    private val loadOlderMessages: LoadOlderMessagesUseCase,
    private val loadNewerMessages: LoadNewerMessagesUseCase,
) {
    data class Page(
        val messages: List<Message>,
        val hasOlder: Boolean,
        val hasNewer: Boolean,
    )

    // A window with the messages read for it, so after the window changes the old list isn't taken for the new.
    private data class Shown(
        val window: Window?,
        val items: List<Message>,
    )

    private val window = MutableStateFlow<Window?>(null)
    private var shown = Shown(null, emptyList())
    private var lastSeq = 0L

    // The window's page, while collected. lastSeq is the chat's newest seq: newer than the window's newest message
    // means there is more below. onNewMessages: messages came at the bottom of the head.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        lastSeqs: Flow<Long>,
        onNewMessages: suspend () -> Unit,
    ): Flow<Page> {
        val shownFlow = window
            .flatMapLatest { w ->
                if (w ==
                    null
                ) {
                    flowOf(Shown(null, emptyList()))
                } else {
                    observeMessages(conversationId, w).map { Shown(w, it) }
                }
            }
            .onEach { now ->
                val previous = shown
                shown = now
                onShown(previous, now, onNewMessages)
            }
        return combine(shownFlow, lastSeqs.onEach { lastSeq = it }) { s, last ->
            Page(
                messages = s.items,
                hasOlder = s.window != null && s.window.fromSeq > 1,
                hasNewer = (s.items.firstOrNull()?.seq ?: last) < last,
            )
        }
    }

    private suspend fun onShown(
        previous: Shown,
        now: Shown,
        onNewMessages: suspend () -> Unit,
    ) {
        val w = now.window ?: return
        val newestSeq = now.items.firstOrNull()?.seq ?: return
        // Not the head: it doesn't grow by itself.
        if (w.toSeq != null) return
        // The same window, a newer newest message: new ones came (the socket, a refresh, a sent message).
        val previousNewestSeq = previous.takeIf { it.window == w }?.items?.firstOrNull()?.seq
        if (previousNewestSeq != null && newestSeq > previousNewestSeq) onNewMessages()
        // At the bottom of an active chat the head grows: the oldest go, far above the screen.
        if (now.items.size > MAX_WINDOW_SIZE) {
            window.update { current ->
                if (current !=
                    w
                ) {
                    current
                } else {
                    w.copy(fromSeq = newestSeq - MAX_WINDOW_SIZE + 1)
                }
            }
        }
    }

    // From the cache, at once: the saved messages show without waiting for the network.
    suspend fun openLatest() {
        window.value = openLatestMessages(conversationId)
    }

    // Opens around seq (loading it when it isn't cached). False: no such message; the window stays.
    suspend fun openAt(seq: Long): Boolean {
        window.value = openAroundMessage(conversationId, seq) ?: return false
        return true
    }

    // The newest from the network. New messages continuing the cache grow the head by themselves. With a gap
    // between the cache and them (the chat wasn't opened for long), the head can't step over it, so it moves to
    // them, but only if it is still the head the user left: a window the user moved, or one opened at the first
    // unread message, stays, and the "to the latest" button (hasNewer) leads down.
    suspend fun refreshLatest() {
        val before = window.value
        val fresh = refreshLatestMessages(conversationId)
        window.update { w -> if (w == before && w?.toSeq == null) fresh else w }
    }

    suspend fun loadOlder() {
        val current = window.value ?: return
        val newFromSeq = loadOlderMessages(conversationId, beforeSeq = current.fromSeq)
        window.update { w ->
            // A jump meanwhile: the window is another one.
            if (w == null || w.fromSeq != current.fromSeq) return@update w
            // Scrolling up, the window stops being the head: its bottom is fixed at the newest message shown.
            // Otherwise new messages would keep growing it, and the cut at the top would take what the user reads.
            val toSeq = w.toSeq ?: newestShownSeq(w) ?: return@update w
            trimNewest(Window(fromSeq = newFromSeq, toSeq = toSeq))
        }
    }

    // True: it reached the chat's end, and the messages the user hasn't seen are at the bottom now.
    suspend fun loadNewer(): Boolean {
        val current = window.value ?: return false
        // The messages shown must be the current window's: right after a jump they may still be the old one's.
        val newestShownSeq = newestShownSeq(current) ?: return false
        // Null: the end of the chat, the window is the head again.
        val newToSeq = loadNewerMessages(conversationId, afterSeq = newestShownSeq)
        var isApplied = false
        window.update { w ->
            if (w == null || w.toSeq != current.toSeq) return@update w
            // A window that would end before it starts: another change came in between.
            if (newToSeq != null && newToSeq < w.fromSeq) return@update w
            isApplied = true
            trimOldest(w.copy(toSeq = newToSeq))
        }
        return isApplied && newToSeq == null
    }

    // The newest message shown, if the messages shown were read for this window.
    private fun newestShownSeq(w: Window): Long? = shown.takeIf { it.window == w }?.items?.firstOrNull()?.seq

    // The window grew up (older). Past MAX_WINDOW_SIZE the newest go, at the bottom the user left.
    // [701, 1000] scrolled up to 651 is [651, 1000], 350 messages: 300 from the top stay, [651, 950].
    private fun trimNewest(w: Window): Window {
        val newestSeq = w.toSeq ?: return w
        if (newestSeq - w.fromSeq + 1 <= MAX_WINDOW_SIZE) return w
        return w.copy(toSeq = w.fromSeq + MAX_WINDOW_SIZE - 1)
    }

    // The window grew down (newer). Past MAX_WINDOW_SIZE the oldest go.
    // [651, 950] scrolled down to 1000 is [651, 1000], 350 messages: 300 from the bottom stay, [701, 1000].
    private fun trimOldest(w: Window): Window {
        // toSeq == null: loadNewer reached the chat's end.
        val newestSeq = w.toSeq ?: lastSeq
        if (newestSeq - w.fromSeq + 1 <= MAX_WINDOW_SIZE) return w
        return w.copy(fromSeq = newestSeq - MAX_WINDOW_SIZE + 1)
    }

    private companion object {
        // The most messages in the window, and so in every re-read of it.
        const val MAX_WINDOW_SIZE = 300
    }
}
