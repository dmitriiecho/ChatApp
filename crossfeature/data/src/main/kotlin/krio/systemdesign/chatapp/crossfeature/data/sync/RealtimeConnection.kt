package krio.systemdesign.chatapp.crossfeature.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import krio.systemdesign.chatapp.core.network.NetworkMonitor
import krio.systemdesign.chatapp.core.network.ServerConfig
import krio.systemdesign.chatapp.core.network.networkJson
import org.koin.core.annotation.Singleton

// The events socket. Open only while the app is on screen: :app runs connectAndListen then and cancels it in the
// background, where Android would cut the socket anyway and pushes bring the events.
// Class public, constructor internal: :app runs it.
@Singleton
class RealtimeConnection internal constructor(
    private val client: HttpClient,
    private val handler: RealtimeHandler,
    private val networkMonitor: NetworkMonitor,
) {
    // Holds the socket until cancelled: connects, listens, and after a drop reconnects with a growing pause
    // (1 s, 2 s, 4 s… up to 30 s) so a server that's down isn't hammered, or as soon as the network comes back.
    suspend fun connectAndListen() {
        var retryDelayMillis = MIN_RETRY_DELAY_MILLIS
        while (true) {
            try {
                client.webSocket(urlString = ServerConfig.EVENTS_URL) {
                    retryDelayMillis = MIN_RETRY_DELAY_MILLIS
                    // Catch up on what was missed first. Events arriving meanwhile wait in incoming and are applied
                    // after; repeats do no harm (version).
                    handler.onReconnected()
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decodeEventOrNull(networkJson.parseToJsonElement(frame.readText()))?.let { handler.onEvent(it) }
                    }
                }
            } catch (e: CancellationException) {
                // The app went to the background: the socket closes.
                throw e
            } catch (e: Exception) {
                // No network, the socket dropped or the sync failed: reconnect below.
            }
            val networkCameBack = withTimeoutOrNull(retryDelayMillis) {
                // Offline now: wait for the network. Online (the server is down, say): just the pause.
                networkMonitor.isOnline.dropWhile { it }.first { it }
            } != null
            retryDelayMillis = if (networkCameBack) {
                MIN_RETRY_DELAY_MILLIS
            } else {
                (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
            }
        }
    }

    private companion object {
        const val MIN_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
    }
}
