package krio.systemdesign.chatapp.server.chat

import kotlin.random.Random
import krio.systemdesign.chatapp.server.db.Database
import krio.systemdesign.chatapp.server.db.Transaction

// The data an empty database starts with, the same every time (a fixed seed). Sized so the app's windows have
// something to page through: more chats per person than the list keeps (150), and a chat longer than the chat
// window (300). Some chats start with unread messages, so the list shows counters and a chat opens at the first one.
object Seed {

    val people = listOf("anna" to "Anna", "max" to "Max", "kate" to "Kate")

    suspend fun fillIfEmpty(
        db: Database,
        nowMillis: Long,
    ) = db.transaction {
        val isEmpty = queryOne("SELECT COUNT(*) FROM users") { it.getLong(1) } == 0L
        if (isEmpty) fill(nowMillis)
    }

    private fun Transaction.fill(nowMillis: Long) {
        val random = Random(SEED)
        people.forEach { (id, name) -> update("INSERT INTO users(id, name, isBot) VALUES (?, ?, 0)", id, name) }
        val bots = (1..BOT_COUNT).map { index -> "bot-%03d".format(index) to botName(index) }
        bots.forEach { (id, name) -> update("INSERT INTO users(id, name, isBot) VALUES (?, ?, 1)", id, name) }
        update("INSERT INTO users(id, name, isBot) VALUES (?, ?, 1)", HISTORIAN, "Historian")

        val chats = mutableListOf<SeedChat>()
        // People with each other, with a little history.
        for (i in people.indices) {
            for (j in i + 1 until people.size) {
                val (a, b) = people[i].first to people[j].first
                chats += SeedChat("dm-$a-$b", title = null, members = listOf(a, b), messages = 20, unread = 0)
            }
        }
        chats += SeedChat(
            id = "team",
            title = "Project team",
            members = people.map { it.first } + bots.take(4).map { it.first },
            messages = 200,
            unread = 12,
        )
        for ((personId, _) in people) {
            // Long enough to page up through and to cut the chat window.
            chats += SeedChat("history-$personId", null, listOf(personId, HISTORIAN), HISTORY_MESSAGES, unread = 40)
            bots.take(BOTS_PER_PERSON).forEach { (botId, _) ->
                chats += SeedChat(
                    id = "$personId-$botId",
                    title = null,
                    members = listOf(personId, botId),
                    messages = random.nextInt(0, 16),
                    unread = if (random.nextInt(4) == 0) random.nextInt(1, 4) else 0,
                )
            }
        }

        // Older chats first: each person's sortKeys then follow the time of the chat's last message.
        val spanMillis = SEED_DAYS * DAY_MILLIS
        val timed = chats.map { it to nowMillis - random.nextLong(spanMillis) }.sortedBy { it.second }
        for ((chat, lastAtMillis) in timed) insertChat(chat, lastAtMillis, random)
    }

    private fun Transaction.insertChat(
        chat: SeedChat,
        lastAtMillis: Long,
        random: Random,
    ) {
        update(
            "INSERT INTO conversations(id, title, isGroup, lastSeq) VALUES (?, ?, ?, ?)",
            chat.id,
            chat.title,
            chat.title != null,
            chat.messages,
        )
        // Messages a few minutes apart, the last one at lastAtMillis.
        for (seq in 1..chat.messages) {
            val sentAtMillis = lastAtMillis - (chat.messages - seq) * MESSAGE_GAP_MILLIS
            update(
                """INSERT INTO messages(id, conversationId, seq, version, authorId, text, sentAtMillis)
                   VALUES (?, ?, ?, 1, ?, ?, ?)""",
                "${chat.id}:$seq",
                chat.id,
                seq,
                chat.members[random.nextInt(chat.members.size)],
                Phrases.pick(random),
                sentAtMillis,
            )
        }
        for (member in chat.members) {
            update("UPDATE users SET lastSortKey = lastSortKey + 1 WHERE id = ?", member)
            update(
                """INSERT INTO participants(conversationId, userId, sortKey, version, lastReadSeq)
                   VALUES (?, ?, (SELECT lastSortKey FROM users WHERE id = ?), 1, ?)""",
                chat.id,
                member,
                member,
                (chat.messages - chat.unread).coerceAtLeast(0),
            )
        }
    }

    private fun botName(index: Int): String {
        val first = FIRST_NAMES[(index - 1) % FIRST_NAMES.size]
        val round = (index - 1) / FIRST_NAMES.size
        return if (round == 0) first else "$first ${round + 1}"
    }

    private data class SeedChat(
        val id: String,
        val title: String?,
        val members: List<String>,
        val messages: Int,
        val unread: Int,
    )

    private const val SEED = 42
    const val BOT_COUNT = 220
    const val BOTS_PER_PERSON = 215
    const val HISTORY_MESSAGES = 3_000
    private const val HISTORIAN = "historian"
    private const val SEED_DAYS = 30L
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val MESSAGE_GAP_MILLIS = 3 * 60 * 1000L

    private val FIRST_NAMES = listOf(
        "Oliver", "Mia", "Noah", "Emma", "Liam", "Ava", "Lucas", "Sofia", "Ethan", "Chloe",
        "Leo", "Zoe", "Hugo", "Iris", "Felix", "Nora", "Theo", "Ruby", "Oscar", "Lily",
        "Adam", "Eva", "Mark", "Lena", "Ivan", "Maria", "Pavel", "Olga", "Denis", "Vera",
    )
}

// What bots and the seed write: short everyday lines.
object Phrases {
    private val all = listOf(
        "Hi! How are you?", "See you tomorrow.", "Sounds good to me.", "Can you send the file?",
        "I'll call you later.", "Running a bit late, sorry.", "That's great news!", "Let me check and get back to you.",
        "Are we still on for Friday?", "Thanks a lot!", "I'm on my way.", "What time works for you?",
        "Just saw your message.", "Let's discuss it at the meeting.", "Can't talk now, text me.",
        "The build is green again.", "Lunch at noon?", "Did you see the new release?", "Good morning!",
        "Have a nice weekend!", "I agree.", "Not sure about that.", "Let's try again tomorrow.",
        "Here are the notes from today.", "Happy birthday!", "Sorry, wrong chat.", "I'll be there in 10 minutes.",
    )

    fun pick(random: Random): String = all[random.nextInt(all.size)]
}
