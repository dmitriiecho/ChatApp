package krio.systemdesign.chatapp.server.chat

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import krio.systemdesign.chatapp.server.db.Database

class SeedTest {

    private val db = Database.inMemory()

    private suspend fun count(
        sql: String,
        vararg args: Any?,
    ): Long = db.transaction { queryOne(sql, *args) { it.getLong(1) }!! }

    @Test
    fun `everyone has more chats than the app's list keeps`() = runTest {
        Seed.fillIfEmpty(db, NOW)

        for ((id, _) in Seed.people) {
            assertThat(count("SELECT COUNT(*) FROM participants WHERE userId = ?", id)).isGreaterThan(150)
        }
    }

    @Test
    fun `the history chat is longer than the app's chat window`() = runTest {
        Seed.fillIfEmpty(db, NOW)

        assertThat(count("SELECT COUNT(*) FROM messages WHERE conversationId = 'history-anna'"))
            .isEqualTo(Seed.HISTORY_MESSAGES.toLong())
    }

    @Test
    fun `each person's sorting keys are unique`() = runTest {
        Seed.fillIfEmpty(db, NOW)

        assertThat(count("SELECT COUNT(DISTINCT sortKey) FROM participants WHERE userId = 'anna'"))
            .isEqualTo(count("SELECT COUNT(*) FROM participants WHERE userId = 'anna'"))
    }

    @Test
    fun `a second start keeps the data`() = runTest {
        Seed.fillIfEmpty(db, NOW)
        val messages = count("SELECT COUNT(*) FROM messages")

        Seed.fillIfEmpty(db, NOW + 1)

        assertThat(count("SELECT COUNT(*) FROM messages")).isEqualTo(messages)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
