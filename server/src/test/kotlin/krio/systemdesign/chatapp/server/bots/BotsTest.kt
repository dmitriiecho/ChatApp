package krio.systemdesign.chatapp.server.bots

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import krio.systemdesign.chatapp.contract.MessagesPageDto
import krio.systemdesign.chatapp.server.ANNA
import krio.systemdesign.chatapp.server.BOT
import krio.systemdesign.chatapp.server.send
import krio.systemdesign.chatapp.server.serverTest

class BotsTest {

    private suspend fun HttpClient.newest() =
        get("/conversations/anna-bot/messages?limit=1").body<MessagesPageDto>().items.single()

    @Test
    fun `a bot answers a person in their direct chat`() = serverTest {
        val scope = CoroutineScope(Dispatchers.Default)
        // No actions of its own: only the answer.
        Bots(db, chat, botsSwitch, Random(1), pause = {
            Duration.INFINITE
        }, replyDelay = { Duration.ZERO }).start(scope)
        try {
            client.send("anna-bot", "Hello, bot")

            val answer = withTimeout(5_000) {
                var newest = client.newest()
                while (newest.authorId != BOT) {
                    delay(20)
                    newest = client.newest()
                }
                newest
            }
            assertThat(answer.seq).isEqualTo(5)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `turned off, a bot doesn't answer`() = serverTest {
        val scope = CoroutineScope(Dispatchers.Default)
        Bots(db, chat, botsSwitch, Random(1), pause = {
            Duration.INFINITE
        }, replyDelay = { Duration.ZERO }).start(scope)
        try {
            botsSwitch.set(false)

            client.send("anna-bot", "Hello, bot")
            // Long enough for an answer with no delay to have come.
            delay(500)

            assertThat(client.newest().authorId).isEqualTo(ANNA)
        } finally {
            scope.cancel()
        }
    }
}
