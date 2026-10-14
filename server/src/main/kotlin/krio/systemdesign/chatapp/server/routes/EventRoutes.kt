package krio.systemdesign.chatapp.server.routes

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.server.events.EventBus
import krio.systemdesign.chatapp.server.serverJson
import krio.systemdesign.chatapp.server.userId

// The user's events, one JSON object per text frame, while the socket is open. Missed ones (the socket was closed
// or too slow) the client catches up on with its sync after reconnecting.
fun Route.eventRoutes(events: EventBus) {
    webSocket("/events") {
        val queue = events.subscribe(call.userId)
        try {
            for (event in queue) {
                send(Frame.Text(serverJson.encodeToString(RealtimeEventDto.serializer(), event)))
            }
        } finally {
            queue.cancel()
        }
    }
}
