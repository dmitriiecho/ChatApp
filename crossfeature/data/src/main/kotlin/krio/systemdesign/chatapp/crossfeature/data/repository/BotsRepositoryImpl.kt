package krio.systemdesign.chatapp.crossfeature.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import krio.systemdesign.chatapp.crossfeature.data.remote.BotsApi
import krio.systemdesign.chatapp.crossfeature.domain.repository.BotsRepository
import org.koin.core.annotation.Singleton

// Held in memory, not in Room: it is the server's state at this moment, not content to show offline. It is read on
// every connection of the socket and follows its bots.changed events.
@Singleton
internal class BotsRepositoryImpl(private val api: BotsApi) : BotsRepository {

    private val _isEnabled = MutableStateFlow<Boolean?>(null)
    override val isEnabled: StateFlow<Boolean?> = _isEnabled.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean): Boolean = api.set(enabled).enabled.also { _isEnabled.value = it }

    // On a connection: a change made while the app was offline came as an event it missed.
    suspend fun refresh() {
        _isEnabled.value = api.get().enabled
    }

    fun onChanged(enabled: Boolean) {
        _isEnabled.value = enabled
    }
}
