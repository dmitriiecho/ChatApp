package krio.systemdesign.chatapp.crossfeature.data

// What the data layer needs from the app, provided by :app's Koin module.

// The key the app is built with (chatapp.apiKey in local.properties); every request carries it.
class ApiKey(val value: String)

// The user's background work: the events socket and the outbox's sending. A sign-out stops it before dropping the
// cache, so nothing of the old user is written after it.
interface SessionWork {
    suspend fun stop()
}

// Sends the outbox when the network is there, also after the app is closed (WorkManager in :app).
interface OutboxScheduler {
    fun schedule()

    fun cancel()
}
