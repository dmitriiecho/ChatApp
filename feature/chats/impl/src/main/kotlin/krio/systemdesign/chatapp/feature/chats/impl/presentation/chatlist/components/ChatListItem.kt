package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.components.badges.CountBadge
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.crossfeature.ui.chat.ChatAvatar
import krio.systemdesign.chatapp.feature.chats.impl.R
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.ChatListState
import krio.systemdesign.chatapp.feature.chats.impl.presentation.common.listTime

@Composable
internal fun ChatListItem(
    item: ChatListState.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text = item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { ChatAvatar(title = item.title) },
        supportingContent = {
            Text(
                text = preview(item.lastMessage),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.lastMessage?.let {
                    Text(
                        text = listTime(it.sentAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.unreadCount > 0) CountBadge(item.unreadCount)
            }
        },
    )
}

@Composable
private fun preview(message: ChatListState.LastMessage?): String = when {
    message == null -> stringResource(R.string.chats_no_messages)
    message.isDeleted -> stringResource(R.string.chats_message_deleted)
    message.isMine -> stringResource(R.string.chats_you, message.text)
    else -> message.text
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatListItemPreview() {
    ChatAppTheme {
        Surface {
            Column {
                ChatListItem(
                    item = ChatListState.Item(
                        id = "1",
                        title = "Project team",
                        lastMessage = ChatListState.LastMessage(
                            "The build is green again.",
                            false,
                            false,
                            PREVIEW_TIME,
                        ),
                        unreadCount = 3,
                    ),
                    onClick = {},
                )
                ChatListItem(
                    item = ChatListState.Item(
                        id = "2",
                        title = "Anna",
                        lastMessage = ChatListState.LastMessage("See you tomorrow.", true, false, PREVIEW_TIME),
                        unreadCount = 0,
                    ),
                    onClick = {},
                )
            }
        }
    }
}

// 2026-10-12 14:05 UTC: a day in the past, so the list shows a date.
internal const val PREVIEW_TIME = 1_791_813_900_000L
