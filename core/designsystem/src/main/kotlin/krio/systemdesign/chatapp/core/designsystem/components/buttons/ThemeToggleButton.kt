package krio.systemdesign.chatapp.core.designsystem.components.buttons

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.R
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.DarkMode
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.LightMode
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.core.designsystem.theme.LocalDarkTheme

// Switches between light and dark. It shows the theme it switches to.
@Composable
fun ThemeToggleButton(
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        if (LocalDarkTheme.current) {
            Icon(AppIcons.LightMode, contentDescription = stringResource(R.string.designsystem_switch_to_light_theme))
        } else {
            Icon(AppIcons.DarkMode, contentDescription = stringResource(R.string.designsystem_switch_to_dark_theme))
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThemeToggleButtonPreview() {
    ChatAppTheme {
        Surface {
            ThemeToggleButton(onToggle = {}, modifier = Modifier.padding(8.dp))
        }
    }
}
