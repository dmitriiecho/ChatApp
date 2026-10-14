package krio.systemdesign.chatapp.server.events

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import krio.systemdesign.chatapp.contract.RealtimeEventDto

// Delivers events to the open sockets of a user: a user may have several devices online at once.
// Each socket has its own bounded queue. A socket that falls that far behind is closed instead of slowing the
// others down: its client reconnects and its sync catches up on what it missed.
class EventBus {

    private val sockets = ConcurrentHashMap<String, MutableSet<Channel<RealtimeEventDto>>>()

    // A queue for one socket of the user. Cancel it when the socket closes.
    fun subscribe(userId: String): ReceiveChannel<RealtimeEventDto> {
        val channel = Channel<RealtimeEventDto>(QUEUE_SIZE)
        sockets.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(channel)
        channel.invokeOnClose { sockets[userId]?.remove(channel) }
        return channel
    }

    fun publish(
        userId: String,
        event: RealtimeEventDto,
    ) {
        sockets[userId]?.forEach { it.deliver(event) }
    }

    // To every open socket, whoever's: for what concerns the whole server.
    fun publishToAll(event: RealtimeEventDto) {
        sockets.values.forEach { userSockets -> userSockets.forEach { it.deliver(event) } }
    }

    private fun Channel<RealtimeEventDto>.deliver(event: RealtimeEventDto) {
        if (trySend(event).isFailure) close()
    }

    private companion object {
        const val QUEUE_SIZE = 256
    }
}
