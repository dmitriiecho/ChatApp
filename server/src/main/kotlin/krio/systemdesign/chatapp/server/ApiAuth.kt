package krio.systemdesign.chatapp.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import java.security.MessageDigest
import krio.systemdesign.chatapp.contract.ApiHeaders

class ApiAuthConfig {
    var apiKey: String = ""
    var isUser: suspend (String) -> Boolean = { false }
}

// Every request needs the app's key, and every one but the user list also a known user's id. Runs before
// routing, so a rejected socket gets 401 instead of an upgrade.
val ApiAuth = createApplicationPlugin("ApiAuth", ::ApiAuthConfig) {
    val apiKey = pluginConfig.apiKey.toByteArray()
    val isUser = pluginConfig.isUser
    onCall { call ->
        val key = call.request.headers[ApiHeaders.API_KEY].orEmpty().toByteArray()
        // Constant time: the comparison doesn't tell how much of a guessed key was right.
        if (!MessageDigest.isEqual(key, apiKey)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@onCall
        }
        // The user list is what the app shows before anyone is picked.
        if (call.request.path() == USERS_PATH) return@onCall
        val userId = call.request.headers[ApiHeaders.USER_ID]
        if (userId == null || !isUser(userId)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@onCall
        }
        call.attributes.put(UserIdKey, userId)
    }
}

// Who sent the request, set by ApiAuth.
val ApplicationCall.userId: String get() = attributes[UserIdKey]

private val UserIdKey = AttributeKey<String>("UserId")

const val USERS_PATH = "/users"
