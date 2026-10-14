package krio.systemdesign.chatapp.crossfeature.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.ConversationsPageDto
import krio.systemdesign.chatapp.contract.UserDto
import krio.systemdesign.chatapp.core.network.callApi
import org.koin.core.annotation.Singleton

// The user's chat list on the server (server/README.md), most active first.
@Singleton
internal class ConversationsApi(private val client: HttpClient) {

    suspend fun latest(limit: Int): ConversationsPageDto = callApi {
        client.get("conversations") { parameter("limit", limit) }.body()
    }

    suspend fun before(
        beforeSortKey: Long,
        limit: Int,
    ): ConversationsPageDto = callApi {
        client.get("conversations") {
            parameter("beforeSortKey", beforeSortKey)
            parameter("limit", limit)
        }.body()
    }

    suspend fun conversation(conversationId: String): ConversationDto = callApi {
        client.get("conversations/${conversationId.encodeURLPathPart()}").body()
    }

    // sinceToken == null (the first start, or the list starts over): no changes and the first token.
    suspend fun changes(
        sinceToken: String?,
        limit: Int,
    ): ConversationChangesDto = callApi {
        client.get("conversations/changes") {
            parameter("since", sinceToken)
            parameter("limit", limit)
        }.body()
    }

    suspend fun users(): List<UserDto> = callApi { client.get("users").body() }
}
