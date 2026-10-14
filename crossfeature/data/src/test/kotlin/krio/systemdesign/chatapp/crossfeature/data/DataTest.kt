package krio.systemdesign.chatapp.crossfeature.data

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import krio.systemdesign.chatapp.contract.ConversationDto
import krio.systemdesign.chatapp.contract.MessageDto
import krio.systemdesign.chatapp.core.network.TestServer
import krio.systemdesign.chatapp.core.network.networkJson
import krio.systemdesign.chatapp.core.network.serverApi
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.MessageEntity
import krio.systemdesign.chatapp.crossfeature.data.remote.BotsApi
import krio.systemdesign.chatapp.crossfeature.data.remote.ChatApi
import krio.systemdesign.chatapp.crossfeature.data.remote.ConversationsApi
import krio.systemdesign.chatapp.crossfeature.data.repository.BotsRepositoryImpl
import krio.systemdesign.chatapp.crossfeature.data.repository.ConversationsRepositoryImpl
import krio.systemdesign.chatapp.crossfeature.data.repository.MessagesRepositoryImpl
import krio.systemdesign.chatapp.crossfeature.data.repository.OutboxRepositoryImpl
import krio.systemdesign.chatapp.crossfeature.data.session.SessionAuthHeaders
import krio.systemdesign.chatapp.crossfeature.data.session.SessionRepositoryImpl
import krio.systemdesign.chatapp.crossfeature.data.session.SessionStore
import krio.systemdesign.chatapp.crossfeature.data.sync.ConversationsSync
import krio.systemdesign.chatapp.crossfeature.data.sync.MessageChangesSync
import krio.systemdesign.chatapp.crossfeature.data.sync.OutboxSender
import krio.systemdesign.chatapp.crossfeature.data.sync.RealtimeHandler
import krio.systemdesign.chatapp.crossfeature.domain.model.User

// The data layer as the app wires it, on an in-memory database and a TestServer, signed in as Anna. Run in a
// Robolectric test: Room needs a Context.
fun dataTest(block: suspend DataTestScope.() -> Unit): TestResult = runTest {
    val server = TestServer()
    val world = DataTestScope(this, server)
    try {
        world.session.login(User(ANNA, "Anna"))
        // The headers come from the session held in memory: wait until it has the sign-in.
        world.sessionStore.current.filterNotNull().first()
        world.block()
    } finally {
        world.close()
    }
}

class DataTestScope internal constructor(
    val testScope: TestScope,
    val server: TestServer,
) {
    internal val db = Room.inMemoryDatabaseBuilder<ChatDatabase>(
        ApplicationProvider.getApplicationContext<Context>(),
    ).build()
    internal val clock = Clock { NOW }
    val scheduler = FakeOutboxScheduler()
    val sessionWork = FakeSessionWork()
    private val dataScope = DataScope()
    internal val sessionStore = SessionStore(db, dataScope)
    internal val session = SessionRepositoryImpl(db, sessionWork, sessionStore)
    internal val authHeaders = SessionAuthHeaders(ApiKey(API_KEY), sessionStore)
    private val client = HttpClient(server.engine) { serverApi(TestServer.BASE_URL, authHeaders) }
    internal val chatApi = ChatApi(client)
    internal val conversationsApi = ConversationsApi(client)
    internal val messageChangesSync = MessageChangesSync(chatApi, db, clock)
    internal val conversationsSync = ConversationsSync(conversationsApi, db)
    internal val messages = MessagesRepositoryImpl(chatApi, conversationsApi, messageChangesSync, sessionStore, db)
    internal val conversations = ConversationsRepositoryImpl(conversationsApi, db, sessionStore)
    internal val outbox = OutboxRepositoryImpl(db, scheduler, clock)
    val outboxSender = OutboxSender(chatApi, db, messages)
    internal val bots = BotsRepositoryImpl(BotsApi(client))
    val realtime = RealtimeHandler(conversationsSync, messageChangesSync, scheduler, bots, db)

    // The session's collector stops before the database closes: reading a closed database would throw in it.
    internal suspend fun close() {
        dataScope.scope.coroutineContext.job.cancelAndJoin()
        client.close()
        db.close()
    }

    // Queues a response the way the server writes it.
    inline fun <reified T> respond(body: T) = server.enqueue(body = networkJson.encodeToString(body))

    // Cached messages seq from..to of a chat, as the server would have sent them.
    internal suspend fun cacheMessages(
        conversationId: String,
        seqs: LongRange,
    ) = db.messageDao().upsertNewer(seqs.map { messageEntity(conversationId, it) })
}

class FakeOutboxScheduler : OutboxScheduler {
    var scheduled = 0
    var cancelled = 0

    override fun schedule() {
        scheduled++
    }

    override fun cancel() {
        cancelled++
    }
}

class FakeSessionWork : SessionWork {
    var stopped = 0

    override suspend fun stop() {
        stopped++
    }
}

internal fun messageEntity(
    conversationId: String,
    seq: Long,
    version: Long = 1,
    text: String = "Message $seq",
    authorId: String = MAX,
) = MessageEntity("$conversationId:$seq", conversationId, seq, version, authorId, "Max", text, seq * 1_000, null, false)

fun messageDto(
    conversationId: String,
    seq: Long,
    version: Long = 1,
    text: String = "Message $seq",
    authorId: String = MAX,
) = MessageDto("$conversationId:$seq", conversationId, seq, version, authorId, "Max", text, seq * 1_000, null, false)

fun conversationDto(
    id: String,
    sortKey: Long,
    version: Long = 1,
    lastSeq: Long = 0,
    unreadCount: Int = 0,
    lastReadSeq: Long = lastSeq,
) = ConversationDto(
    id = id,
    title = id,
    avatarUrl = null,
    sortKey = sortKey,
    version = version,
    unreadCount = unreadCount,
    lastSeq = lastSeq,
    lastReadSeq = lastReadSeq,
    lastMessage = if (lastSeq == 0L) null else messageDto(id, lastSeq),
)

const val ANNA = "anna"
const val MAX = "max"
const val API_KEY = "test-key"
const val NOW = 1_800_000_000_000L
