package krio.systemdesign.chatapp.feature.login.impl.presentation.login

import krio.systemdesign.chatapp.core.mvi.Reducer

internal object LoginReducer : Reducer<LoginState, LoginMutation> {

    override fun reduce(
        state: LoginState,
        mutation: LoginMutation,
    ): LoginState = when (mutation) {
        LoginMutation.LoadingStarted -> state.copy(users = LoginState.Users.Loading)
        is LoginMutation.UsersLoaded -> state.copy(users = LoginState.Users.Loaded(mutation.users))
        LoginMutation.LoadingFailed -> state.copy(users = LoginState.Users.Error)
        LoginMutation.SigningIn -> state.copy(isSigningIn = true)
    }
}
