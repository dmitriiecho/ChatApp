package krio.systemdesign.chatapp.feature.chats.impl.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object ChatsRoutes {
    // The feature's first screen: the app opens the feature with it.
    @Serializable
    data object ChatList : NavKey

    @Serializable
    internal data class Chat(val chatId: String) : NavKey
}

// The feature's keys, for saving the back stack: the app adds them to its SavedStateConfiguration.
val chatsNavKeys = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(ChatsRoutes.ChatList::class)
        subclass(ChatsRoutes.Chat::class)
    }
}
