package krio.systemdesign.chatapp.server.routes

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import krio.systemdesign.chatapp.contract.ApiHeaders
import krio.systemdesign.chatapp.contract.BotsDto
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.server.MAX
import krio.systemdesign.chatapp.server.bots.BotsSwitch
import krio.systemdesign.chatapp.server.serverJson
import krio.systemdesign.chatapp.server.serverTest

class BotsRoutesTest {

    private suspend fun HttpClient.setBots(
        enabled: Boolean,
        asUser: String? = null,
    ): HttpResponse = put("/bots") {
        asUser?.let { header(ApiHeaders.USER_ID, it) }
        contentType(ContentType.Application.Json)
        setBody(BotsDto(enabled))
    }

    @Test
    fun `the bots are on by default`() = serverTest {
        assertThat(client.get("/bots").body<BotsDto>()).isEqualTo(BotsDto(enabled = true))
    }

    @Test
    fun `turned off, they stay off`() = serverTest {
        val response = client.setBots(enabled = false)

        assertThat(response.body<BotsDto>()).isEqualTo(BotsDto(enabled = false))
        assertThat(client.get("/bots").body<BotsDto>()).isEqualTo(BotsDto(enabled = false))
    }

    @Test
    fun `the switch outlives a restart`() = serverTest {
        client.setBots(enabled = false)

        // What a restart reads: the same database, a new switch.
        assertThat(BotsSwitch.load(db, events).isEnabled.value).isFalse()
    }

    @Test
    fun `a change reaches everyone's sockets`() = serverTest {
        client.webSocket("/events") {
            client.setBots(enabled = false, asUser = MAX)

            val frame = incoming.receive() as Frame.Text
            assertThat(serverJson.decodeFromString(RealtimeEventDto.serializer(), frame.readText()))
                .isEqualTo(RealtimeEventDto.BotsChanged(enabled = false))
        }
    }

    @Test
    fun `the switch needs the key`() = serverTest {
        val response = client.put("/bots") {
            header(ApiHeaders.API_KEY, "wrong")
            contentType(ContentType.Application.Json)
            setBody(BotsDto(enabled = false))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(botsSwitch.isEnabled.value).isEqualTo(true)
    }
}
