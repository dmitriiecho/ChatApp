package krio.systemdesign.chatapp.core.designsystem.components.bars

import android.content.res.Configuration
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import krio.systemdesign.chatapp.core.designsystem.components.buttons.NavigateBackIconButton
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme

// The app's top bar: a tone of its own, the same as the bars at the bottom of a screen, so the content reads framed.
// It reaches under the status bar (the app draws edge to edge), which takes its colour too.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        // The same when the content scrolls under it: the bar already stands apart.
        colors = TopAppBarDefaults.topAppBarColors(containerColor = color, scrolledContainerColor = color),
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppTopBarPreview() {
    ChatAppTheme {
        AppTopBar(title = { Text("Chats") }, navigationIcon = { NavigateBackIconButton(onClick = {}) })
    }
}
