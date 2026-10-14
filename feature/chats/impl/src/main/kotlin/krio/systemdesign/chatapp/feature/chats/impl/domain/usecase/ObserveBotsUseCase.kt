package krio.systemdesign.chatapp.feature.chats.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import krio.systemdesign.chatapp.crossfeature.domain.repository.BotsRepository
import org.koin.core.annotation.Factory

@Factory
internal class ObserveBotsUseCase(private val repository: BotsRepository) {
    operator fun invoke(): Flow<Boolean?> = repository.isEnabled
}
