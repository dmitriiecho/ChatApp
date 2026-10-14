package krio.systemdesign.chatapp.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import krio.systemdesign.chatapp.contract.ApiHeaders
import krio.systemdesign.chatapp.server.bots.Bots
import krio.systemdesign.chatapp.server.bots.BotsSwitch
import krio.systemdesign.chatapp.server.chat.ChatService
import krio.systemdesign.chatapp.server.chat.Seed
import krio.systemdesign.chatapp.server.db.Database
import krio.systemdesign.chatapp.server.events.EventBus
import krio.systemdesign.chatapp.server.routes.RateLimits
import krio.systemdesign.chatapp.server.routes.botsRoutes
import krio.systemdesign.chatapp.server.routes.conversationRoutes
import krio.systemdesign.chatapp.server.routes.eventRoutes
import krio.systemdesign.chatapp.server.routes.messageRoutes
import krio.systemdesign.chatapp.server.routes.userRoutes

// The systemd service sets PORT, DB_PATH, API_KEY and BOTS_ENABLED (see deploy.sh); the defaults fit a run from
// server/, except API_KEY, which has none: a server without a key would let anyone in.
fun main() {
    val port = System.getenv("PORT")?.toInt() ?: DEFAULT_PORT
    val apiKey = checkNotNull(System.getenv("API_KEY")?.takeIf { it.isNotBlank() }) { "API_KEY is not set" }
    val db = Database.open(System.getenv("DB_PATH") ?: DEFAULT_DB_PATH)
    runBlocking { Seed.fillIfEmpty(db, System.currentTimeMillis()) }
    val events = EventBus()
    val chat = ChatService(db, events, clock = System::currentTimeMillis)
    val botsSwitch = runBlocking { BotsSwitch.load(db, events) }
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(chat, events, botsSwitch, apiKey)
        // BOTS_ENABLED=false: no bots at all, whatever the switch says; it then only keeps its value.
        if (System.getenv("BOTS_ENABLED") != "false") Bots(db, chat, botsSwitch, Random.Default).start(this)
    }.start(wait = true)
}

fun Application.module(
    chat: ChatService,
    events: EventBus,
    botsSwitch: BotsSwitch,
    apiKey: String,
) {
    install(ContentNegotiation) {
        json(serverJson)
    }
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
    install(ApiAuth) {
        this.apiKey = apiKey
        isUser = chat::isUser
    }
    install(RequestBodyLimit) {
        bodyLimit { MAX_BODY_BYTES }
    }
    install(RateLimit) {
        register(RateLimits.SEND) {
            rateLimiter(limit = RateLimits.SENDS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.request.headers[ApiHeaders.USER_ID].orEmpty() }
        }
        register(RateLimits.READ) {
            rateLimiter(limit = RateLimits.READS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.request.headers[ApiHeaders.USER_ID].orEmpty() }
        }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest) }
        exception<PayloadTooLargeException> { call, _ -> call.respond(HttpStatusCode.PayloadTooLarge) }
    }
    routing {
        userRoutes(chat)
        conversationRoutes(chat)
        messageRoutes(chat)
        eventRoutes(events)
        botsRoutes(botsSwitch)
    }
}

private const val DEFAULT_PORT = 8081
private const val DEFAULT_DB_PATH = "chat.db"
private const val MAX_BODY_BYTES = 16 * 1024L
