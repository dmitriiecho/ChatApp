package krio.systemdesign.chatapp.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import krio.systemdesign.chatapp.server.chat.ChatService
import krio.systemdesign.chatapp.server.userId

fun Route.conversationRoutes(chat: ChatService) {
    // Most active first; beforeSortKey continues below the previous page.
    get("/conversations") {
        val page = chat.conversations(call.userId, call.longParam("beforeSortKey"), call.limitParam(MAX_PAGE_SIZE))
        call.respond(page)
    }

    // A fixed segment wins over {conversationId} in Ktor's routing, so "changes" is never taken for an id.
    get("/conversations/changes") {
        val since = call.request.queryParameters["since"]
        call.respond(chat.conversationChanges(call.userId, since, call.limitParam(MAX_CHANGES)))
    }

    get("/conversations/{conversationId}") {
        val conversation = chat.conversation(call.userId, call.conversationId())
        if (conversation == null) call.respond(HttpStatusCode.NotFound) else call.respond(conversation)
    }
}
