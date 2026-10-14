package krio.systemdesign.chatapp.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import krio.systemdesign.chatapp.contract.ApiHeaders
import krio.systemdesign.chatapp.contract.ReadRequestDto
import krio.systemdesign.chatapp.contract.SendMessageRequestDto
import krio.systemdesign.chatapp.server.bots.BotsSwitch
import krio.systemdesign.chatapp.server.chat.ChatService
import krio.systemdesign.chatapp.server.db.Database
import krio.systemdesign.chatapp.server.events.EventBus

// The server in memory on the test's data, with a clock that stands still and a client that reads JSON as the app
// does and signs every request as Anna with the test key.
fun serverTest(
    journalLimit: Int = 1_000,
    data: suspend TestData.() -> Unit = { standardData() },
    block: suspend ServerTestScope.() -> Unit,
) = testApplication {
    val db = Database.inMemory()
    val events = EventBus()
    val chat = ChatService(db, events, clock = { NOW }, journalLimit = journalLimit)
    val botsSwitch = BotsSwitch.load(db, events)
    TestData(db).data()
    application { module(chat, events, botsSwitch, TEST_API_KEY) }
    val client = createClient {
        install(ContentNegotiation) { json(serverJson) }
        install(WebSockets)
        // Only when the request has none: defaultRequest appends to a request's own header, and two values would
        // reach the server as one "max, anna".
        defaultRequest {
            if (!headers.contains(ApiHeaders.API_KEY)) header(ApiHeaders.API_KEY, TEST_API_KEY)
            if (!headers.contains(ApiHeaders.USER_ID)) header(ApiHeaders.USER_ID, ANNA)
        }
    }
    ServerTestScope(this, client, chat, db, events, botsSwitch).block()
    db.close()
}

class ServerTestScope(
    val app: ApplicationTestBuilder,
    val client: HttpClient,
    val chat: ChatService,
    val db: Database,
    val events: EventBus,
    val botsSwitch: BotsSwitch,
)

// Rows put straight into the tables, so a test sets up exactly the chats it needs.
class TestData(private val db: Database) {

    suspend fun user(
        id: String,
        name: String = id.replaceFirstChar { it.uppercase() },
        isBot: Boolean = false,
    ) = db.transaction { update("INSERT INTO users(id, name, isBot) VALUES (?, ?, ?)", id, name, isBot) }

    // Messages sent a second apart, oldest first; each member then gets the next sortKey, so a chat added later is
    // higher in every member's list.
    suspend fun chat(
        id: String,
        members: List<String>,
        messages: List<Pair<String, String>> = emptyList(),
        title: String? = null,
        lastReadSeq: Map<String, Long> = emptyMap(),
    ) = db.transaction {
        update(
            "INSERT INTO conversations(id, title, isGroup, lastSeq) VALUES (?, ?, ?, ?)",
            id,
            title,
            title != null,
            messages.size,
        )
        messages.forEachIndexed { index, (authorId, text) ->
            val seq = index + 1
            update(
                """INSERT INTO messages(id, conversationId, seq, version, authorId, text, sentAtMillis)
                   VALUES (?, ?, ?, 1, ?, ?, ?)""",
                "$id:$seq",
                id,
                seq,
                authorId,
                text,
                START + seq * 1_000L,
            )
        }
        for (member in members) {
            update("UPDATE users SET lastSortKey = lastSortKey + 1 WHERE id = ?", member)
            update(
                """INSERT INTO participants(conversationId, userId, sortKey, version, lastReadSeq)
                   VALUES (?, ?, (SELECT lastSortKey FROM users WHERE id = ?), 1, ?)""",
                id,
                member,
                member,
                lastReadSeq[member] ?: messages.size.toLong(),
            )
        }
    }

    // Anna and Max write to each other; Anna has a chat with a bot and an unread message in it.
    suspend fun standardData() {
        user(ANNA)
        user(MAX)
        user(BOT, name = "Oliver", isBot = true)
        chat(
            "anna-bot",
            listOf(ANNA, BOT),
            listOf(BOT to "Hi!", ANNA to "Hello", BOT to "How are you?"),
            lastReadSeq = mapOf(ANNA to 2),
        )
        chat(DM, listOf(ANNA, MAX), listOf(ANNA to "Hi, Max", MAX to "Hi, Anna"))
    }
}

suspend fun HttpClient.send(
    conversationId: String,
    text: String,
    clientId: String = "c-$text",
    asUser: String = ANNA,
): HttpResponse = post("/conversations/$conversationId/messages") {
    header(ApiHeaders.USER_ID, asUser)
    contentType(ContentType.Application.Json)
    setBody(SendMessageRequestDto(clientId = clientId, text = text))
}

suspend fun HttpClient.read(
    conversationId: String,
    seq: Long,
): HttpResponse = post("/conversations/$conversationId/read") {
    contentType(ContentType.Application.Json)
    setBody(ReadRequestDto(seq))
}

// A sample from api-samples/: the JSON the app and the server agree on. The app's tests read the same files.
fun apiSample(name: String): JsonElement = serverJson.parseToJsonElement(Path("api-samples", name).readText())

// Sends a request from api-samples/requests.json ("GET /users"), the one the app sends, with a sample body.
// The app expects a success, so any other status fails the test with the status and the request in its message.
suspend fun HttpClient.sendApiRequest(
    name: String,
    body: JsonElement? = null,
): HttpResponse {
    val (method, target) = apiSample("requests.json").jsonObject.getValue(name).jsonPrimitive.content.split(" ")
    return request(target) {
        this.method = HttpMethod.parse(method)
        expectSuccess = true
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }
}

const val TEST_API_KEY = "test-key"
const val ANNA = "anna"
const val MAX = "max"
const val BOT = "bot"
const val DM = "dm-anna-max"
const val START = 1_700_000_000_000L
const val NOW = 1_800_000_000_000L
