package krio.systemdesign.chatapp.server.bots

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import krio.systemdesign.chatapp.server.chat.ChatService
import krio.systemdesign.chatapp.server.chat.Phrases
import krio.systemdesign.chatapp.server.db.Database
import org.slf4j.LoggerFactory

// The other side of the chats, so the app gets events without a second person at hand: now and then a bot writes
// to one of its chats or edits or deletes its own recent message, and a bot answers a person who writes to it.
// Turned off by the switch, they do none of it.
class Bots(
    private val db: Database,
    private val chat: ChatService,
    private val switch: BotsSwitch,
    private val random: Random,
    private val pause: () -> Duration = { random.nextInt(10, 31).seconds },
    private val replyDelay: () -> Duration = { random.nextInt(2, 6).seconds },
) {
    fun start(scope: CoroutineScope) {
        scope.launch { act() }
        scope.launch { answer(scope) }
    }

    private suspend fun act() {
        while (currentCoroutineContext().isActive) {
            delay(pause())
            if (switch.isEnabled.value) runCatchingLogged { actOnce() }
        }
    }

    // One action in a random chat that has a bot: mostly a new message, sometimes an edit or a deletion.
    suspend fun actOnce() {
        val (conversationId, botId) = randomBotChat() ?: return
        val roll = random.nextInt(100)
        val own = if (roll < EDIT_PERCENT + DELETE_PERCENT) lastOwnMessage(conversationId, botId) else null
        when {
            own != null && roll < EDIT_PERCENT -> chat.edit(own, Phrases.pick(random))
            own != null -> chat.delete(own)
            else -> chat.send(botId, conversationId, clientId = null, Phrases.pick(random))
        }
    }

    // A person wrote to a bot in a direct chat: the bot answers a few seconds later.
    private suspend fun answer(scope: CoroutineScope) {
        chat.posted.collect { message ->
            val botId = directChatBot(message.conversationId) ?: return@collect
            scope.launch {
                delay(replyDelay())
                // Checked after the pause: no answer if the bots were turned off meanwhile.
                if (!switch.isEnabled.value) return@launch
                runCatchingLogged { chat.send(botId, message.conversationId, clientId = null, Phrases.pick(random)) }
            }
        }
    }

    private suspend fun randomBotChat(): Pair<String, String>? = db.transaction {
        val count = queryOne("SELECT COUNT(*) FROM participants p JOIN users u ON u.id = p.userId WHERE u.isBot = 1") {
            it.getLong(1)
        }!!
        if (count == 0L) return@transaction null
        queryOne(
            """SELECT p.conversationId, p.userId FROM participants p JOIN users u ON u.id = p.userId
               WHERE u.isBot = 1 LIMIT 1 OFFSET ?""",
            random.nextLong(count),
        ) { it.getString(1) to it.getString(2) }
    }

    private suspend fun lastOwnMessage(
        conversationId: String,
        botId: String,
    ): String? = db.transaction {
        queryOne(
            "SELECT id FROM messages WHERE conversationId = ? AND authorId = ? AND isDeleted = 0 ORDER BY seq DESC LIMIT 1",
            conversationId,
            botId,
        ) { it.getString(1) }
    }

    // The bot of a direct chat between one person and one bot; null for other chats.
    private suspend fun directChatBot(conversationId: String): String? = db.transaction {
        val members = query(
            """SELECT u.id, u.isBot FROM participants p JOIN users u ON u.id = p.userId
               JOIN conversations c ON c.id = p.conversationId WHERE p.conversationId = ? AND c.isGroup = 0""",
            conversationId,
        ) { it.getString(1) to it.getBoolean(2) }
        members.singleOrNull { it.second }?.first?.takeIf { members.size == 2 }
    }

    private suspend fun runCatchingLogged(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed bot action is no reason to stop the bots.
            log.warn("Bot action failed", e)
        }
    }

    private companion object {
        const val EDIT_PERCENT = 5
        const val DELETE_PERCENT = 2
        val log = LoggerFactory.getLogger(Bots::class.java)
    }
}
