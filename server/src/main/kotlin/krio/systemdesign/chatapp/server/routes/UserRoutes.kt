package krio.systemdesign.chatapp.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import krio.systemdesign.chatapp.server.USERS_PATH
import krio.systemdesign.chatapp.server.chat.ChatService

// The people one can sign in as; bots aren't listed.
fun Route.userRoutes(chat: ChatService) {
    get(USERS_PATH) {
        call.respond(chat.users())
    }
}
