package krio.systemdesign.chatapp.server

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import krio.systemdesign.chatapp.contract.ApiHeaders
import krio.systemdesign.chatapp.contract.UserDto

class ApiAuthTest {

    @Test
    fun `a request with a wrong key is rejected`() = serverTest {
        val response = client.get("/users") { header(ApiHeaders.API_KEY, "wrong") }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `the user list needs only the key and shows people, not bots`() = serverTest {
        val anonymous = app.createClient {
            install(ContentNegotiation) { json(serverJson) }
        }

        val users = anonymous.get("/users") { header(ApiHeaders.API_KEY, TEST_API_KEY) }.body<List<UserDto>>()

        assertThat(users).containsExactly(UserDto("anna", "Anna"), UserDto("max", "Max"))
    }

    @Test
    fun `chats need a known user`() = serverTest {
        val response = client.get("/conversations?limit=10") { header(ApiHeaders.USER_ID, "nobody") }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `a bot can't sign in`() = serverTest {
        val response = client.get("/conversations?limit=10") { header(ApiHeaders.USER_ID, BOT) }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }
}
