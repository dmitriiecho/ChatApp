package krio.systemdesign.chatapp.feature.login.impl.presentation.login

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import krio.systemdesign.chatapp.core.designsystem.components.bars.AppTopBar
import krio.systemdesign.chatapp.core.designsystem.components.screenstates.EmptyState
import krio.systemdesign.chatapp.core.designsystem.icons.AppIcons
import krio.systemdesign.chatapp.core.designsystem.icons.symbols.ChatBubble
import krio.systemdesign.chatapp.core.designsystem.theme.ChatAppTheme
import krio.systemdesign.chatapp.core.designsystem.theme.Spacing
import krio.systemdesign.chatapp.crossfeature.domain.model.User
import krio.systemdesign.chatapp.crossfeature.ui.chat.ChatAvatar
import krio.systemdesign.chatapp.feature.login.impl.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LoginScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun LoginScreen(
    state: LoginState,
    onIntent: (LoginIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = { Text(stringResource(R.string.login_title)) }) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (val users = state.users) {
            LoginState.Users.Loading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoginState.Users.Loaded -> LazyColumn(modifier = contentModifier) {
                items(users.items, key = { it.id }) { user ->
                    ListItem(
                        headlineContent = { Text(user.name) },
                        leadingContent = { ChatAvatar(title = user.name) },
                        modifier = Modifier.clickable { onIntent(LoginIntent.UserClicked(user)) },
                    )
                }
            }
            LoginState.Users.Error -> Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(Spacing.ItemSpacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(icon = AppIcons.ChatBubble, message = stringResource(R.string.login_error))
                Button(onClick = { onIntent(LoginIntent.RetryClicked) }) { Text(stringResource(R.string.login_retry)) }
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenPreview() {
    ChatAppTheme {
        LoginScreen(
            state = LoginState(users = LoginState.Users.Loaded(listOf(User("anna", "Anna"), User("max", "Max")))),
            onIntent = {},
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenErrorPreview() {
    ChatAppTheme {
        LoginScreen(state = LoginState(users = LoginState.Users.Error), onIntent = {})
    }
}
