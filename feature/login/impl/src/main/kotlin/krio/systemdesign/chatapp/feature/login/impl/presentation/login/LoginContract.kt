package krio.systemdesign.chatapp.feature.login.impl.presentation.login

import krio.systemdesign.chatapp.core.mvi.StoreAccess
import krio.systemdesign.chatapp.crossfeature.domain.model.User

internal data class LoginState(
    val users: Users = Users.Loading,
    // A sign-in is being saved: further taps are ignored.
    val isSigningIn: Boolean = false,
) {
    sealed interface Users {
        data object Loading : Users

        data class Loaded(val items: List<User>) : Users

        data object Error : Users
    }
}

internal sealed interface LoginIntent {
    data class UserClicked(val user: User) : LoginIntent

    data object RetryClicked : LoginIntent
}

internal sealed interface LoginMutation {
    data object LoadingStarted : LoginMutation

    data class UsersLoaded(val users: List<User>) : LoginMutation

    data object LoadingFailed : LoginMutation

    data object SigningIn : LoginMutation
}

// None: after a sign-in the app opens the chats by itself.
internal sealed interface LoginEffect

// The Store as the Executor sees it; the full type is long.
internal typealias LoginStore = StoreAccess<LoginState, LoginMutation, LoginEffect>
