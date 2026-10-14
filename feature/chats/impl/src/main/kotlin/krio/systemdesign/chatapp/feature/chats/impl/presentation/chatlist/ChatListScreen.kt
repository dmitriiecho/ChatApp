package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import krio.systemdesign.chatapp.core.composeutils.effects.ObserveEffects
import krio.systemdesign.chatapp.core.designsystem.components.bars.AppTopBar
import krio.systemdesign.chatapp.core.designsystem.components.buttons.ThemeToggleButton
import krio.systemdesign.chatapp.core.designsystem.components.screenstates.EmptyState
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.ChatBubble
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.Logout
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.feature.chats.impl.R
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.components.BotsBar
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.components.ChatListItem
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.components.PREVIEW_TIME
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ChatListScreen(
    onOpenChat: (chatId: String) -> Unit,
    onToggleTheme: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.chats_error)
    val botsErrorMessage = stringResource(R.string.chats_bots_error)

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is ChatListEffect.OpenChat -> navigate { onOpenChat(effect.chatId) }
            ChatListEffect.ShowError -> showSnackbar(snackbarHostState, errorMessage)
            ChatListEffect.ShowBotsError -> showSnackbar(snackbarHostState, botsErrorMessage)
        }
    }

    ChatListScreen(
        state = state,
        onToggleTheme = onToggleTheme,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
internal fun ChatListScreen(
    state: ChatListState,
    onToggleTheme: () -> Unit,
    onIntent: (ChatListIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(stringResource(R.string.chats_title))
                        state.userName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    ThemeToggleButton(onToggle = onToggleTheme)
                    IconButton(onClick = { onIntent(ChatListIntent.LogoutClicked) }) {
                        Icon(AppIcons.Logout, contentDescription = stringResource(R.string.chats_logout))
                    }
                },
            )
        },
        bottomBar = {
            val bots = state.bots as? ChatListState.Bots.Known
            BotsBar(
                isOn = bots?.isOn ?: false,
                isEnabled = bots != null && bots.requested == null,
                onToggle = { onIntent(ChatListIntent.BotsToggled(it)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (val chats = state.chats) {
            ChatListState.Chats.Loading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ChatListState.Chats.Loaded -> if (chats.items.isEmpty()) {
                EmptyState(
                    icon = AppIcons.ChatBubble,
                    message = stringResource(R.string.chats_empty),
                    modifier = contentModifier,
                )
            } else {
                Chats(state = state, items = chats.items, onIntent = onIntent, modifier = contentModifier)
            }
        }
    }
}

@Composable
private fun Chats(
    state: ChatListState,
    items: List<ChatListState.Item>,
    onIntent: (ChatListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    ReachedEdges(listState, state, onIntent)
    FollowTop(listState, topId = items.firstOrNull()?.id)
    LazyColumn(state = listState, modifier = modifier) {
        // key = id: when a chat jumps to the top or the window moves, Compose keeps the visible rows in place.
        items(items, key = { it.id }, contentType = { "chat" }) { item ->
            ChatListItem(
                item = item,
                onClick = { onIntent(ChatListIntent.ChatClicked(item.id)) },
                modifier = Modifier.animateItem(),
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// A chat that rose to the top shows when the user is at the top: LazyColumn keeps the first visible chat in place
// by its key, so otherwise the new one would land above the screen's edge. Further down, the place is kept.
// SideEffect runs before the list measures the change, while it is still where the user left it.
@Composable
private fun FollowTop(
    listState: LazyListState,
    topId: String?,
) {
    val shownTopId = remember { arrayOfNulls<String>(1) }
    SideEffect {
        val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (topId != shownTopId[0] && isAtTop) listState.requestScrollToItem(0)
        shownTopId[0] = topId
    }
}

// Asks to move the window near its top or bottom. Checked again when the number of chats or the flags change, not
// only when the user reaches an edge: after a short load the user may still be at the edge, and the list would stop.
// The chats are counted rather than all rows: the loading row comes and goes with every try, and offline that
// would repeat the tries forever.
@Composable
private fun ReachedEdges(
    listState: LazyListState,
    state: ChatListState,
    onIntent: (ChatListIntent) -> Unit,
) {
    val currentState by rememberUpdatedState(state)
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            val chats = (currentState.chats as? ChatListState.Chats.Loaded)?.items?.size ?: 0
            EdgeCheck(
                isNearTop = total > 0 && first <= EDGE_THRESHOLD,
                isNearBottom = total > 0 && last >= total - EDGE_THRESHOLD,
                chatCount = chats,
                hasAbove = currentState.hasAbove,
                hasMore = currentState.hasMore,
            )
        }
            .distinctUntilChanged()
            .collect { check ->
                if (check.isNearTop && check.hasAbove) currentOnIntent(ChatListIntent.ReachedTop)
                if (check.isNearBottom && check.hasMore) currentOnIntent(ChatListIntent.ReachedBottom)
            }
    }
}

// Everything the "move the window?" decision depends on: any change, and it is checked again.
private data class EdgeCheck(
    val isNearTop: Boolean,
    val isNearBottom: Boolean,
    val chatCount: Int,
    val hasAbove: Boolean,
    val hasMore: Boolean,
)

private const val EDGE_THRESHOLD = 10

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatListScreenPreview() {
    ChatAppTheme {
        ChatListScreen(
            state = ChatListState(
                chats = ChatListState.Chats.Loaded(
                    listOf(
                        ChatListState.Item(
                            id = "1",
                            title = "Anna",
                            lastMessage = ChatListState.LastMessage(
                                "Yes, at 10 by the station.",
                                false,
                                false,
                                PREVIEW_TIME,
                            ),
                            unreadCount = 2,
                        ),
                        ChatListState.Item(id = "2", title = "Max", lastMessage = null, unreadCount = 0),
                    ),
                ),
                userName = "Kate",
                bots = ChatListState.Bots.Known(isEnabled = true),
            ),
            onToggleTheme = {},
            onIntent = {},
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatListScreenEmptyPreview() {
    ChatAppTheme {
        ChatListScreen(
            state = ChatListState(chats = ChatListState.Chats.Loaded(emptyList())),
            onToggleTheme = {},
            onIntent = {},
        )
    }
}
