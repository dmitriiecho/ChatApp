package krio.systemdesign.chatapp.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.plus
import krio.systemdesign.chatapp.feature.chats.impl.presentation.navigation.ChatsRoutes
import krio.systemdesign.chatapp.feature.chats.impl.presentation.navigation.chatsEntries
import krio.systemdesign.chatapp.feature.chats.impl.presentation.navigation.chatsNavKeys
import krio.systemdesign.chatapp.feature.login.impl.presentation.navigation.LoginRoutes
import krio.systemdesign.chatapp.feature.login.impl.presentation.navigation.loginEntries
import krio.systemdesign.chatapp.feature.login.impl.presentation.navigation.loginNavKeys

// The back stack follows the session: signed out it is the sign-in alone, signed in it starts at the chat list.
// A sign-in or a sign-out replaces it whole, so Back never leads to the other side.
@Composable
fun AppNavHost(
    isSignedIn: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Survives configuration changes and process death: the keys are saved with kotlinx.serialization.
    val backStack = rememberNavBackStack(navKeysConfiguration, startKey(isSignedIn))
    val navigator = remember(backStack) { AppNavigator(backStack) }
    // Entries are kept until the back stack changes, so they call the latest callback through this state.
    val currentOnToggleTheme by rememberUpdatedState(onToggleTheme)

    LaunchedEffect(isSignedIn) {
        val isOnSignIn = backStack.lastOrNull() == LoginRoutes.Login
        if (isSignedIn == isOnSignIn) {
            backStack.clear()
            backStack.add(startKey(isSignedIn))
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = navigator::goBack,
        entryDecorators = listOf(
            // Must come first: the other decorators save state through it.
            rememberSaveableStateHolderNavEntryDecorator(),
            // Each screen gets its own ViewModels (koinViewModel()), cleared when the screen leaves the back stack.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Screens switch at once: the default is a 700 ms crossfade, which feels slow.
        transitionSpec = { noTransition },
        popTransitionSpec = { noTransition },
        predictivePopTransitionSpec = { noTransition },
        entryProvider = entryProvider {
            loginEntries()
            chatsEntries(navigator, onToggleTheme = { currentOnToggleTheme() })
        },
    )
}

private val noTransition = ContentTransform(EnterTransition.None, ExitTransition.None)

private fun startKey(isSignedIn: Boolean): NavKey = if (isSignedIn) ChatsRoutes.ChatList else LoginRoutes.Login

// Every key the back stack can hold, so it can be saved on every platform (rememberNavBackStack without
// a configuration finds them by reflection, on Android only).
private val navKeysConfiguration = SavedStateConfiguration {
    serializersModule = chatsNavKeys + loginNavKeys
}
