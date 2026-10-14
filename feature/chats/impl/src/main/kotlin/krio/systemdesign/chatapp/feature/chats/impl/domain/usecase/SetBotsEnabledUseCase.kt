package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import krio.systemdesign.chatapp.crossfeature.domain.repository.BotsRepository
import org.koin.core.annotation.Factory

// Turns the server's bots on or off for everyone; returns the value now in force.
@Factory
internal class SetBotsEnabledUseCase(private val repository: BotsRepository) {
    suspend operator fun invoke(enabled: Boolean): Boolean = repository.setEnabled(enabled)
}
