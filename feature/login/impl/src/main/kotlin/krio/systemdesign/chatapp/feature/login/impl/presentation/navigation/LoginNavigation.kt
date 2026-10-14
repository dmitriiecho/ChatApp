package krio.systemdesign.chatapp.feature.login.impl.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import krio.systemdesign.chatapp.feature.login.impl.presentation.login.LoginScreen

// No callback for a sign-in: the app follows the session and replaces the back stack with the chats.
fun EntryProviderScope<NavKey>.loginEntries() {
    entry<LoginRoutes.Login> {
        LoginScreen()
    }
}
