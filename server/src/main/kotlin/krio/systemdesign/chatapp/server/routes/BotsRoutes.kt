package krio.systemdesign.chatapp.server.routes

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import krio.systemdesign.chatapp.contract.BotsDto
import krio.systemdesign.chatapp.server.bots.BotsSwitch

// The bots' switch, for the whole server. PUT answers with the value now in force.
fun Route.botsRoutes(switch: BotsSwitch) {
    get("/bots") {
        call.respond(BotsDto(enabled = switch.isEnabled.value))
    }

    put("/bots") {
        switch.set(call.receive<BotsDto>().enabled)
        call.respond(BotsDto(enabled = switch.isEnabled.value))
    }
}
