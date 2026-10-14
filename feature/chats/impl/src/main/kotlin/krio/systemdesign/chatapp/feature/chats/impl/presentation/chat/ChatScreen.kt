package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat

import android.content.res.Configuration
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import krio.systemdesign.chatapp.core.composeutils.effects.ObserveEffects
import krio.systemdesign.chatapp.core.designsystem.components.bars.AppTopBar
import krio.systemdesign.chatapp.core.designsystem.components.buttons.NavigateBackIconButton
import krio.systemdesign.chatapp.core.designsystem.components.screenstates.EmptyState
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.ArrowDownward
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.ChatBubble
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.SwapVert
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.core.designsystem.theme.Spacing
import krio.systemdesign.chatapp.crossfeature.domain.model.Message
import krio.systemdesign.chatapp.crossfeature.domain.model.OutgoingMessage
import krio.systemdesign.chatapp.crossfeature.ui.chat.ChatAvatar
import krio.systemdesign.chatapp.feature.chats.impl.R
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.components.MessageBubble
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.components.MessageInput
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.components.SendStatus
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.components.UnreadDivider
import krio.systemdesign.chatapp.feature.chats.impl.presentation.common.messageTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel { parametersOf(chatId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.chat_error)
    val rows by rememberUpdatedState(chatRows(state))
    val scope = rememberCoroutineScope()

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            ChatEffect.NavigateBack -> navigate { onBack() }
            ChatEffect.ShowError -> showSnackbar(snackbarHostState, errorMessage)
            ChatEffect.ScrollToBottom -> scope.launch { listState.scrollToItem(0) }
            is ChatEffect.ScrollToSeq -> scope.launch { listState.scrollToMessage(effect.seq) { rows } }
            // Compose keeps the item that was on screen in place, so a new message lands below the bottom edge.
            // At the very bottom the user is following the chat: scroll down to it. Reading higher up: leave it.
            ChatEffect.NewMessagesArrived -> if (listState.firstVisibleItemIndex <= AUTOSCROLL_THRESHOLD) {
                scope.launch {
                    // The effect may come a frame before the messages reach the list.
                    withFrameNanos { }
                    listState.animateScrollToItem(0)
                }
            }
        }
    }

    ChatScreen(
        state = state,
        onIntent = viewModel::onIntent,
        listState = listState,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun ChatScreen(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = { state.title?.let { ChatTitle(it) } },
                navigationIcon = { NavigateBackIconButton(onClick = { onIntent(ChatIntent.BackClicked) }) },
                actions = {
                    IconButton(onClick = { onIntent(ChatIntent.JumpSomewhereClicked) }, enabled = !state.isEmpty) {
                        Icon(AppIcons.SwapVert, contentDescription = stringResource(R.string.chat_jump_somewhere))
                    }
                },
            )
        },
        bottomBar = {
            MessageInput(state = state.input, canSend = state.canSend, onSend = { onIntent(ChatIntent.SendClicked) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val rows = chatRows(state)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (rows.isEmpty() && state.isEmpty) {
                EmptyState(
                    icon = AppIcons.ChatBubble,
                    message = stringResource(R.string.chat_no_messages),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Messages(rows = rows, state = state, onIntent = onIntent, listState = listState)
            }
            if (state.hasNewer) {
                SmallFloatingActionButton(
                    onClick = { onIntent(ChatIntent.JumpToLatest) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.ScreenPadding),
                ) {
                    Icon(AppIcons.ArrowDownward, contentDescription = stringResource(R.string.chat_jump_to_latest))
                }
            }
        }
    }
}

@Composable
private fun ChatTitle(title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(title = title, size = 36.dp)
        Text(title)
    }
}

// Reversed: index 0, the newest, is at the bottom; the list starts there and stays when new messages come.
@Composable
private fun Messages(
    rows: List<ChatRow>,
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    listState: LazyListState,
) {
    ReachedEdges(listState, rows, state, onIntent)
    ReportSeen(listState, rows, onIntent)
    FollowBottom(listState, bottomKey = rows.firstOrNull()?.key)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(Spacing.ScreenPadding),
        // A short chat sits at the bottom, by the input, not at the top.
        verticalArrangement = Arrangement.spacedBy(Spacing.ItemSpacing, Alignment.Bottom),
    ) {
        items(rows, key = { it.key }, contentType = { it.contentType }) { row ->
            when (row) {
                is ChatRow.Item -> MessageBubble(
                    text = row.message.text,
                    isMine = row.message.isMine,
                    time = messageTime(row.message.sentAtMillis),
                    author = row.message.authorName.takeIf { row.showAuthor },
                    isEdited = row.message.editedAtMillis != null,
                    isDeleted = row.message.isDeleted,
                )
                is ChatRow.Outgoing -> MessageBubble(
                    text = row.message.text,
                    isMine = true,
                    time = "",
                    status = if (row.message.isFailed) SendStatus.Failed else SendStatus.Sending,
                    onRetry = { onIntent(ChatIntent.RetryClicked(row.message.clientId)) },
                )
                ChatRow.Divider -> UnreadDivider()
                is ChatRow.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// A new row at the bottom (a message sent or received) shows when the user is at the very bottom: LazyColumn keeps the
// visible row in place by its key, so otherwise the new one would land below the screen's edge. Reading higher up,
// the place is kept. SideEffect runs before the list measures the change, while it is still where the user left it.
@Composable
private fun FollowBottom(
    listState: LazyListState,
    bottomKey: String?,
) {
    val shownBottomKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        val isAtBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (bottomKey != shownBottomKey[0] && isAtBottom) listState.requestScrollToItem(0)
        shownBottomKey[0] = bottomKey
    }
}

// Asks for older or newer messages near an edge. Checked again when the number of messages or the flags change,
// not only when the user reaches an edge: after a short load the user may still be at the edge, or hasNewer may
// turn true while the user is at the bottom, and paging would stop. Messages are counted rather than rows: the
// loading row comes and goes with every try, and offline that would repeat the tries forever.
@Composable
private fun ReachedEdges(
    listState: LazyListState,
    rows: List<ChatRow>,
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
) {
    val currentRows by rememberUpdatedState(rows)
    val currentState by rememberUpdatedState(state)
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val bottom = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val top = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = currentRows.size
            EdgeCheck(
                isNearTop = total > 0 && top >= total - EDGE_THRESHOLD,
                isNearBottom = total > 0 && bottom <= EDGE_THRESHOLD,
                messageCount = currentState.messages.size,
                hasOlder = currentState.hasOlder,
                hasNewer = currentState.hasNewer,
            )
        }
            .distinctUntilChanged()
            .collect { check ->
                if (check.isNearTop && check.hasOlder) currentOnIntent(ChatIntent.ReachedTop)
                if (check.isNearBottom && check.hasNewer) currentOnIntent(ChatIntent.ReachedBottom)
            }
    }
}

// The newest message on screen: the chat is read up to it while it's open.
@Composable
private fun ReportSeen(
    listState: LazyListState,
    rows: List<ChatRow>,
    onIntent: (ChatIntent) -> Unit,
) {
    val currentRows by rememberUpdatedState(rows)
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { (currentRows.getOrNull(it.index) as? ChatRow.Item)?.message?.seq }
                .maxOrNull()
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnIntent(ChatIntent.MessagesSeen(it)) }
    }
}

// Waits for the message (the first unread, a jump's) to reach the list, then puts it near the top of the screen,
// with the divider above the first unread and the newer messages below. Gives up after a while: the page may not
// come offline.
private suspend fun LazyListState.scrollToMessage(
    seq: Long,
    rows: () -> List<ChatRow>,
) {
    val index = withTimeoutOrNull(SCROLL_WAIT_MILLIS) {
        snapshotFlow { rows().indexOfFirst { it is ChatRow.Item && it.message.seq == seq }.takeIf { it >= 0 } }
            .filterNotNull()
            .first()
    } ?: return
    // A reversed list puts the item at the bottom edge; scrolling back by most of the screen brings it near the
    // top.
    scrollToItem(index)
    scrollBy(-layoutInfo.viewportSize.height * UNREAD_AT_HEIGHT)
}

// What the list shows, bottom first (it is reversed): the messages not sent yet when the window is the head, then
// the messages newest first, with the divider above the first unread one, and loading rows at the edges.
internal fun chatRows(state: ChatState): List<ChatRow> = buildList {
    if (!state.hasNewer) state.outgoing.asReversed().forEach { add(ChatRow.Outgoing(it)) }
    if (state.isLoadingNewer) add(ChatRow.Loading("loading_newer"))
    state.messages.forEachIndexed { index, message ->
        // The author above someone else's first message in a row; the next one up is the older one.
        val older = state.messages.getOrNull(index + 1)
        add(ChatRow.Item(message, showAuthor = !message.isMine && older?.authorId != message.authorId))
        if (message.seq == state.unreadDividerSeq) add(ChatRow.Divider)
    }
    if (state.isLoadingOlder) add(ChatRow.Loading("loading_older"))
}

internal sealed interface ChatRow {
    val key: String
    val contentType: String

    data class Item(
        val message: Message,
        val showAuthor: Boolean,
    ) : ChatRow {
        override val key get() = message.id
        override val contentType get() = "message"
    }

    data class Outgoing(val message: OutgoingMessage) : ChatRow {
        override val key get() = "outgoing:${message.clientId}"
        override val contentType get() = "message"
    }

    data object Divider : ChatRow {
        override val key = "divider"
        override val contentType = "divider"
    }

    data class Loading(override val key: String) : ChatRow {
        override val contentType get() = "loading"
    }
}

// Everything the "load at the edge?" decision depends on: any change, and it is checked again.
private data class EdgeCheck(
    val isNearTop: Boolean,
    val isNearBottom: Boolean,
    val messageCount: Int,
    val hasOlder: Boolean,
    val hasNewer: Boolean,
)

private const val EDGE_THRESHOLD = 10

// How close to the bottom the user is for new messages to be scrolled to.
private const val AUTOSCROLL_THRESHOLD = 3
private const val SCROLL_WAIT_MILLIS = 3_000L

// Where the first unread message goes: this share of the screen up from the bottom.
private const val UNREAD_AT_HEIGHT = 0.7f

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenPreview() {
    ChatAppTheme {
        ChatScreen(
            state = ChatState(
                input = remember { TextFieldState("See you") },
                title = "Anna",
                messages = listOf(
                    previewMessage(seq = 3, text = "Great, see you!", isMine = false),
                    previewMessage(seq = 2, text = "Yes, at 10 by the station.", isMine = true),
                    previewMessage(seq = 1, text = "Hi! Are we still on for tomorrow?", isMine = false),
                ),
                outgoing = listOf(OutgoingMessage("c1", "anna", "Bringing the tickets", 0, isFailed = false)),
                unreadDividerSeq = 3,
            ),
            onIntent = {},
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenEmptyPreview() {
    ChatAppTheme {
        ChatScreen(
            state = ChatState(input = remember { TextFieldState() }, title = "Max", isEmpty = true),
            onIntent = {},
        )
    }
}

// 14:05 UTC on 2026-10-12 and a minute per message.
private fun previewMessage(
    seq: Long,
    text: String,
    isMine: Boolean,
) = Message(
    id = "anna:$seq",
    conversationId = "anna",
    seq = seq,
    authorId = if (isMine) "kate" else "anna",
    authorName = if (isMine) "Kate" else "Anna",
    isMine = isMine,
    text = text,
    sentAtMillis = 1_791_813_900_000L + seq * 60_000,
    editedAtMillis = null,
    isDeleted = false,
)
