package krio.systemdesign.chatapp.crossfeature.data.local

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import krio.systemdesign.chatapp.crossfeature.data.conversationDto
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationDaoTest {

    private suspend fun ChatDatabase.setBoundary(sortKey: Long) = conversationListMetaDao().upsert(
        ConversationListMetaEntity(loadedDownToSortKey = sortKey, hasMore = true, syncToken = "0"),
    )

    @Test
    fun `nothing shows before the first page`() = dataTest {
        db.conversationDao().upsertNewer(listOf(conversationDto("a", sortKey = 5).toEntity()))

        assertThat(db.conversationDao().observeLoaded(0, 10).first()).isEmpty()
    }

    @Test
    fun `the list shows chats above the boundary, most active first`() = dataTest {
        db.setBoundary(5)
        db.conversationDao().upsertNewer(listOf(4L, 5L, 9L).map { conversationDto("c$it", sortKey = it).toEntity() })

        val ids = db.conversationDao().observeLoaded(offset = 0, limit = 10).first().map { it.id }

        assertThat(ids).containsExactly("c9", "c5")
    }

    @Test
    fun `a window skips the chats above its offset`() = dataTest {
        db.setBoundary(Long.MIN_VALUE)
        db.conversationDao().upsertNewer((1L..5L).map { conversationDto("c$it", sortKey = it).toEntity() })

        val ids = db.conversationDao().observeLoaded(offset = 1, limit = 2).first().map { it.id }

        assertThat(ids).containsExactly("c4", "c3")
    }

    @Test
    fun `a sync keeps a chat that is cached or rose above the boundary, not one deep down`() = dataTest {
        db.setBoundary(10)
        db.conversationDao().upsertNewer(listOf(conversationDto("cached", sortKey = 3).toEntity()))

        db.conversationDao().upsertIfRelevant(
            listOf(
                conversationDto("cached", sortKey = 4, version = 2).toEntity(),
                conversationDto("risen", sortKey = 20).toEntity(),
                conversationDto("deep", sortKey = 2).toEntity(),
            ),
        )

        assertThat(db.conversationDao().version("cached")).isEqualTo(2)
        assertThat(db.conversationDao().version("risen")).isEqualTo(1)
        assertThat(db.conversationDao().version("deep")).isEqualTo(null)
    }
}
