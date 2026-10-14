package krio.systemdesign.chatapp.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import krio.systemdesign.chatapp.crossfeature.data.OutboxScheduler
import krio.systemdesign.chatapp.crossfeature.data.SessionWork
import krio.systemdesign.chatapp.crossfeature.data.sync.RealtimeConnection
import krio.systemdesign.chatapp.crossfeature.domain.model.Session
import org.koin.core.annotation.Singleton

// The events socket while someone is signed in and the app is on screen (the process is STARTED):
//   on screen       the socket connects, and its connection catches up on what was missed;
//   in background   it closes; Android would cut it anyway;
//   back            it connects again.
// ProcessLifecycleOwner reports the background about 700 ms late, so a rotation doesn't reconnect.
// Also the user's background work for a sign-out: stop waits until the socket is closed and the outbox's sending
// is cancelled, so nothing of the old user is written after the cache is dropped.
@Singleton
internal class RealtimeRunner(
    private val connection: RealtimeConnection,
    private val outboxScheduler: OutboxScheduler,
) : SessionWork {

    private val process = ProcessLifecycleOwner.get()
    private var socket: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(sessions: Flow<Session?>) {
        process.lifecycleScope.launch {
            sessions.map { it?.userId }.distinctUntilChanged().collectLatest { userId ->
                if (userId == null) return@collectLatest
                coroutineScope {
                    socket =
                        launch {
                            process.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                connection.connectAndListen()
                            }
                        }
                }
            }
        }
    }

    override suspend fun stop() {
        socket?.cancelAndJoin()
        socket = null
        outboxScheduler.cancel()
    }
}
