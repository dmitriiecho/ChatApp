package krio.systemdesign.chatapp.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import krio.systemdesign.chatapp.contract.ReadRequestDto
import krio.systemdesign.chatapp.contract.SendMessageRequestDto
import krio.systemdesign.chatapp.server.chat.ChatService
import krio.systemdesign.chatapp.server.chat.MessagesPage
import krio.systemdesign.chatapp.server.userId

fun Route.messageRoutes(chat: ChatService) {
    // Newest first. beforeSeq, afterSeq and aroundSeq exclude each other; none of them is the latest page.
    get("/conversations/{conversationId}/messages") {
        val limit = call.limitParam(MAX_PAGE_SIZE)
        val before = call.longParam("beforeSeq")
        val after = call.longParam("afterSeq")
        val around = call.longParam("aroundSeq")
        if (listOfNotNull(before, after, around).size > 1) {
            throw BadRequestException("beforeSeq, afterSeq and aroundSeq exclude each other")
        }
        val page = when {
            before != null -> MessagesPage.Before(before, limit)
            after != null -> MessagesPage.After(after, limit)
            around != null -> MessagesPage.Around(around, limit)
            else -> MessagesPage.Latest(limit)
        }
        val messages = chat.messages(call.userId, call.conversationId(), page)
        if (messages == null) call.respond(HttpStatusCode.NotFound) else call.respond(messages)
    }

    get("/conversations/{conversationId}/messages/changes") {
        val since = call.request.queryParameters["since"]
        val changes = chat.messageChanges(call.userId, call.conversationId(), since, call.limitParam(MAX_CHANGES))
        if (changes == null) call.respond(HttpStatusCode.NotFound) else call.respond(changes)
    }

    rateLimit(RateLimits.SEND) {
        post("/conversations/{conversationId}/messages") {
            val request = call.receive<SendMessageRequestDto>()
            val text = request.text.trim()
            if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) {
                throw BadRequestException("text must have 1 to $MAX_TEXT_LENGTH characters")
            }
            if (request.clientId.isBlank() || request.clientId.length > MAX_CLIENT_ID_LENGTH) {
                throw BadRequestException("clientId must have 1 to $MAX_CLIENT_ID_LENGTH characters")
            }
            val response = chat.send(call.userId, call.conversationId(), request.clientId, text)
            if (response == null) call.respond(HttpStatusCode.NotFound) else call.respond(response)
        }
    }

    rateLimit(RateLimits.READ) {
        post("/conversations/{conversationId}/read") {
            val request = call.receive<ReadRequestDto>()
            val conversation = chat.markRead(call.userId, call.conversationId(), request.seq)
            if (conversation == null) call.respond(HttpStatusCode.NotFound) else call.respond(conversation)
        }
    }
}

// Writes allowed per user, so a script with the key can't flood the chats.
object RateLimits {
    val SEND = RateLimitName("send")
    val READ = RateLimitName("read")
    const val SENDS_PER_MINUTE = 10
    const val READS_PER_MINUTE = 30
}

const val MAX_TEXT_LENGTH = 4_000
private const val MAX_CLIENT_ID_LENGTH = 64
