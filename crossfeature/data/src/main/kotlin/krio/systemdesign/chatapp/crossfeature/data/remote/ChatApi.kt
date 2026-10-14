package krio.systemdesign.chatapp.crossfeature.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.MessageChangesDto
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.contract.ReadRequestDto
import krio.systemdesign.chatapp.contract.SendMessageRequestDto
import krio.systemdesign.chatapp.contract.SendMessageResponseDto
import krio.systemdesign.chatapp.core.network.callApi
import org.koin.core.annotation.Singleton

// One chat's messages on the server (server/README.md). Pages come newest first. Every call goes through callApi,
// so whatever fails comes out as IOException.
@Singleton
internal class ChatApi(private val client: HttpClient) {

    suspend fun latest(
        conversationId: String,
        limit: Int,
    ): MessagesPageDto = page(conversationId, limit)

    suspend fun before(
        conversationId: String,
        beforeSeq: Long,
        limit: Int,
    ): MessagesPageDto = page(conversationId, limit, "beforeSeq" to beforeSeq)

    suspend fun after(
        conversationId: String,
        afterSeq: Long,
        limit: Int,
    ): MessagesPageDto = page(conversationId, limit, "afterSeq" to afterSeq)

    suspend fun around(
        conversationId: String,
        seq: Long,
        limit: Int,
    ): MessagesPageDto = page(conversationId, limit, "aroundSeq" to seq)

    // sinceToken == null (the chat's first sync): no changes and the chat's first token.
    suspend fun changes(
        conversationId: String,
        sinceToken: String?,
        limit: Int,
    ): MessageChangesDto = callApi {
        client.get("conversations/${conversationId.encodeURLPathPart()}/messages/changes") {
            parameter("since", sinceToken)
            parameter("limit", limit)
        }.body()
    }

    suspend fun send(
        conversationId: String,
        clientId: String,
        text: String,
    ): SendMessageResponseDto = callApi {
        client.post("conversations/${conversationId.encodeURLPathPart()}/messages") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequestDto(clientId = clientId, text = text))
        }.body()
    }

    suspend fun read(
        conversationId: String,
        seq: Long,
    ): ConversationDto = callApi {
        client.post("conversations/${conversationId.encodeURLPathPart()}/read") {
            contentType(ContentType.Application.Json)
            setBody(ReadRequestDto(seq))
        }.body()
    }

    private suspend fun page(
        conversationId: String,
        limit: Int,
        anchor: Pair<String, Long>? = null,
    ): MessagesPageDto = callApi {
        client.get("conversations/${conversationId.encodeURLPathPart()}/messages") {
            parameter("limit", limit)
            anchor?.let { (name, seq) -> parameter(name, seq) }
        }.body()
    }
}
