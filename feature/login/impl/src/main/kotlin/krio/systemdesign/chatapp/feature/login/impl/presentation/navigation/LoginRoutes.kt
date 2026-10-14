package krio.systemdesign.chatapp.feature.login.impl.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object LoginRoutes {
    // The feature's only screen: the app shows it while nobody is signed in.
    @Serializable
    data object Login : NavKey
}

// The feature's keys, for saving the back stack: the app adds them to its SavedStateConfiguration.
val loginNavKeys = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(LoginRoutes.Login::class)
    }
}
