package krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.components.bars.AppBottomBar
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.feature.chats.impl.R

// The bar under the chat list: the server's bots, on or off for everyone. The whole bar toggles, so it is one
// target for a tap and for TalkBack.
@Composable
internal fun BotsBar(
    isOn: Boolean,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomBar(
        modifier = modifier.toggleable(value = isOn, enabled = isEnabled, role = Role.Switch, onValueChange = onToggle),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(stringResource(R.string.chats_bots), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.chats_bots_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The bar handles the taps.
        Switch(checked = isOn, onCheckedChange = null, enabled = isEnabled)
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BotsBarPreview() {
    ChatAppTheme {
        Column {
            BotsBar(isOn = true, isEnabled = true, onToggle = {})
            BotsBar(isOn = false, isEnabled = true, onToggle = {})
            BotsBar(isOn = false, isEnabled = false, onToggle = {})
        }
    }
}
