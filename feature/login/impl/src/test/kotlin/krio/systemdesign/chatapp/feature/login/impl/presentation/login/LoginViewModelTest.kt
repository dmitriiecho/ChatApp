package krio.systemdesign.chatapp.feature.login.impl.presentation.login

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import krio.systemdesign.chatapp.core.composeutils.keepCollecting
import krio.systemdesign.chatapp.core.composeutils.viewModelTest
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import krio.systemdesign.chatapp.crossfeature.domain.model.User
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import krio.systemdesign.chatapp.crossfeature.domain.repository.UsersRepository
import krio.systemdesign.chatapp.feature.login.impl.domain.usecase.GetUsersUseCase
import krio.systemdesign.chatapp.feature.login.impl.domain.usecase.LoginUseCase

class LoginViewModelTest {

    private val anna = User("anna", "Anna")

    private class TestUsers(var fail: Boolean = false) : UsersRepository {
        override suspend fun users(): List<User> =
            if (fail) throw IOException("offline") else listOf(User("anna", "Anna"))
    }

    private class TestSession : SessionRepository {
        val logins = mutableListOf<User>()
        override val session: Flow<Session?> = MutableStateFlow(null)

        override suspend fun login(user: User) {
            logins += user
        }

        override suspend fun logout() = Unit
    }

    private val users = TestUsers()
    private val session = TestSession()

    private fun viewModel() = LoginViewModel(StoreFactory(), GetUsersUseCase(users), LoginUseCase(session))

    @Test
    fun `the screen lists the users`() = viewModelTest {
        val viewModel = viewModel()
        keepCollecting(viewModel.state)

        assertThat(viewModel.state.value.users).isEqualTo(LoginState.Users.Loaded(listOf(anna)))
    }

    @Test
    fun `a failed load can be retried`() = viewModelTest {
        users.fail = true
        val viewModel = viewModel()
        assertThat(viewModel.state.value.users).isEqualTo(LoginState.Users.Error)

        users.fail = false
        viewModel.onIntent(LoginIntent.RetryClicked)

        assertThat(viewModel.state.value.users).isEqualTo(LoginState.Users.Loaded(listOf(anna)))
    }

    @Test
    fun `a tap signs in once`() = viewModelTest {
        val viewModel = viewModel()

        viewModel.onIntent(LoginIntent.UserClicked(anna))
        viewModel.onIntent(LoginIntent.UserClicked(anna))

        assertThat(session.logins).containsExactly(anna)
    }
}
