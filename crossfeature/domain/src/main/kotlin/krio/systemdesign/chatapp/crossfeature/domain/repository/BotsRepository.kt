package krio.systemdesign.chatapp.crossfeature.domain.repository

import kotlinx.coroutines.flow.Flow

// The server's bots, on or off for everyone.
interface BotsRepository {

    // Null until the app has reached the server: offline a remembered value could be wrong.
    val isEnabled: Flow<Boolean?>

    // Returns the value now in force. IOException without a network.
    suspend fun setEnabled(enabled: Boolean): Boolean
}
