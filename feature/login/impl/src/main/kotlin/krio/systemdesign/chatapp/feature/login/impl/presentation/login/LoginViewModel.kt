package krio.systemdesign.chatapp.feature.login.impl.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.feature.login.impl.domain.usecase.GetUsersUseCase
import krio.systemdesign.chatapp.feature.login.impl.domain.usecase.LoginUseCase
import org.koin.core.annotation.KoinViewModel

// Holds the screen's Store, so it outlives a rotation.
@KoinViewModel
internal class LoginViewModel(
    storeFactory: StoreFactory,
    getUsers: GetUsersUseCase,
    login: LoginUseCase,
) : ViewModel() {

    private val store = storeFactory.create(
        name = "Login",
        initialState = LoginState(),
        reducer = LoginReducer,
        executor = LoginExecutor(getUsers, login),
        scope = viewModelScope,
    )

    val state: StateFlow<LoginState> = store.state

    fun onIntent(intent: LoginIntent) = store.accept(intent)
}
