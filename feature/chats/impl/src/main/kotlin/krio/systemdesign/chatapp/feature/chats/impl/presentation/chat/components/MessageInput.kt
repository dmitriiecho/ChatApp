package krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.components.bars.AppBottomBar
import krio.systemdesign.chatapp.core.designsystem.components.inputs.RoundedTextField
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.Send
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.feature.chats.impl.R

// The bar under the messages: the field and a round send button that lights up once there is something to send.
@Composable
internal fun MessageInput(
    state: TextFieldState,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // At the field's last line when the text grows to several.
    AppBottomBar(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        RoundedTextField(
            state = state,
            placeholder = stringResource(R.string.chat_message_hint),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            // Centred on a one-line field, which is 56 dp high.
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(48.dp),
        ) {
            Icon(imageVector = AppIcons.Send, contentDescription = stringResource(R.string.chat_send))
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageInputPreview() {
    ChatAppTheme {
        Column {
            MessageInput(state = remember { TextFieldState("See you") }, canSend = true, onSend = {})
            MessageInput(state = remember { TextFieldState() }, canSend = false, onSend = {})
            MessageInput(
                state = remember {
                    TextFieldState("A longer message that doesn't fit on one line and goes on to a second")
                },
                canSend = true,
                onSend = {},
            )
        }
    }
}
