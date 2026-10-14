package krio.systemdesign.chatapp.feature.chats.impl.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import krio.systemdesign.chatapp.core.composeutils.navigation.Navigator
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chat.ChatScreen
import krio.systemdesign.chatapp.feature.chats.impl.presentation.chatlist.ChatListScreen

// The theme belongs to the app: the chat list only switches it.
fun EntryProviderScope<NavKey>.chatsEntries(
    navigator: Navigator,
    onToggleTheme: () -> Unit,
) {
    entry<ChatsRoutes.ChatList> {
        ChatListScreen(
            onOpenChat = { chatId -> navigator.navigate(ChatsRoutes.Chat(chatId)) },
            onToggleTheme = onToggleTheme,
        )
    }
    entry<ChatsRoutes.Chat> { key ->
        ChatScreen(chatId = key.chatId, onBack = navigator::goBack)
    }
}
