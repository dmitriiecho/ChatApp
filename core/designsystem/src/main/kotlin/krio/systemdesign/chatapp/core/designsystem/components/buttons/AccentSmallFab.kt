package krio.systemdesign.chatapp.core.designsystem.components.buttons

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.ArrowDownward
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme

// A small floating button in the accent colour. Material's default, primaryContainer, is the pale tone of the
// content it floats over (the user's own messages in a chat), and the two blend.
@Composable
fun AccentSmallFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccentSmallFabPreview() {
    ChatAppTheme {
        Surface {
            AccentSmallFab(
                icon = AppIcons.ArrowDownward,
                contentDescription = "Down",
                onClick = {},
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
