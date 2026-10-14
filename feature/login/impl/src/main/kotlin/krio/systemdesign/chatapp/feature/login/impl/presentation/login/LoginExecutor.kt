package krio.systemdesign.chatapp.feature.login.impl.presentation.login

import java.io.IOException
import krio.systemdesign.chatapp.core.mvi.Executor
import krio.systemdesign.chatapp.feature.login.impl.domain.usecase.GetUsersUseCase
import krio.systemdesign.chatapp.feature.login.impl.domain.usecase.LoginUseCase

internal class LoginExecutor(
    private val getUsers: GetUsersUseCase,
    private val login: LoginUseCase,
) : Executor<LoginIntent, LoginState, LoginMutation, LoginEffect> {

    override suspend fun onStart(store: LoginStore) = load(store)

    override suspend fun execute(
        intent: LoginIntent,
        store: LoginStore,
    ) {
        when (intent) {
            is LoginIntent.UserClicked -> if (!store.state.isSigningIn) {
                store.dispatch(LoginMutation.SigningIn)
                login(intent.user)
            }
            LoginIntent.RetryClicked -> if (store.state.users is LoginState.Users.Error) load(store)
        }
    }

    private suspend fun load(store: LoginStore) {
        store.dispatch(LoginMutation.LoadingStarted)
        try {
            store.dispatch(LoginMutation.UsersLoaded(getUsers()))
        } catch (e: IOException) {
            store.dispatch(LoginMutation.LoadingFailed)
        }
    }
}
