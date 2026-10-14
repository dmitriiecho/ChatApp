package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.Error
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.Schedule
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.feature.chats.impl.R

// Where a message of the user's stands: on the server, on its way, or not sent.
internal enum class SendStatus { Sent, Sending, Failed }

// The user's own messages on the right, the others' on the left, with the author above when it's someone else's
// first in a row. A failed one is tapped to send it again.
@Composable
internal fun MessageBubble(
    text: String,
    isMine: Boolean,
    time: String,
    modifier: Modifier = Modifier,
    author: String? = null,
    isEdited: Boolean = false,
    isDeleted: Boolean = false,
    status: SendStatus = SendStatus.Sent,
    onRetry: () -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(if (status == SendStatus.Failed) Modifier.clickable(onClick = onRetry) else Modifier),
            shape = RoundedCornerShape(16.dp),
            color = with(MaterialTheme.colorScheme) { if (isMine) primaryContainer else surfaceContainerHighest },
            contentColor = with(MaterialTheme.colorScheme) { if (isMine) onPrimaryContainer else onSurface },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isDeleted) {
                    Text(
                        text = stringResource(R.string.chat_message_deleted),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(text = text, style = MaterialTheme.typography.bodyLarge)
                }
                Footer(
                    time = time,
                    isEdited = isEdited && !isDeleted,
                    status = status,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun Footer(
    time: String,
    isEdited: Boolean,
    status: SendStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (status ==
            SendStatus.Failed
        ) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val label = when (status) {
            SendStatus.Sent -> if (isEdited) stringResource(R.string.chat_edited, time) else time
            SendStatus.Sending -> stringResource(R.string.chat_sending)
            SendStatus.Failed -> stringResource(R.string.chat_not_sent)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        when (status) {
            SendStatus.Sent -> Unit
            // The label says it: no description of its own.
            SendStatus.Sending -> Icon(AppIcons.Schedule, contentDescription = null, Modifier.size(12.dp), tint = color)
            SendStatus.Failed -> Icon(AppIcons.Error, contentDescription = null, Modifier.size(12.dp), tint = color)
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageBubblePreview() {
    ChatAppTheme {
        Surface {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageBubble(
                    text = "Hi! Are we still on for tomorrow?",
                    isMine = false,
                    time = "14:05",
                    author = "Anna",
                )
                MessageBubble(text = "Yes, at 10 by the station.", isMine = true, time = "14:06", isEdited = true)
                MessageBubble(text = "", isMine = false, time = "14:07", isDeleted = true)
                MessageBubble(text = "On my way", isMine = true, time = "", status = SendStatus.Sending)
                MessageBubble(text = "Running late", isMine = true, time = "", status = SendStatus.Failed)
            }
        }
    }
}
