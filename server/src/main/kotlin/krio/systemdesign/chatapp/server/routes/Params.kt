package krio.systemdesign.chatapp.server.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException

// A query parameter that must be a number in range when present; anything else is a 400.
internal fun ApplicationCall.longParam(name: String): Long? {
    val value = request.queryParameters[name] ?: return null
    return value.toLongOrNull() ?: throw BadRequestException("$name must be a number")
}

internal fun ApplicationCall.limitParam(max: Int): Int {
    val limit = request.queryParameters["limit"]?.toIntOrNull()
    if (limit == null || limit !in 1..max) throw BadRequestException("limit must be from 1 to $max")
    return limit
}

internal fun ApplicationCall.conversationId(): String = parameters["conversationId"]!!

internal const val MAX_PAGE_SIZE = 100
internal const val MAX_CHANGES = 500
