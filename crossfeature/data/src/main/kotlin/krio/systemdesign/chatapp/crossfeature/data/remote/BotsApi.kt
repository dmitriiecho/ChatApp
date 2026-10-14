package krio.systemdesign.chatapp.crossfeature.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import krio.systemdesign.chatapp.contract.BotsDto
import krio.systemdesign.chatapp.core.network.callApi
import org.koin.core.annotation.Singleton

// The server's bots switch (server/README.md).
@Singleton
internal class BotsApi(private val client: HttpClient) {

    suspend fun get(): BotsDto = callApi { client.get("bots").body() }

    suspend fun set(enabled: Boolean): BotsDto = callApi {
        client.put("bots") {
            contentType(ContentType.Application.Json)
            setBody(BotsDto(enabled))
        }.body()
    }
}
