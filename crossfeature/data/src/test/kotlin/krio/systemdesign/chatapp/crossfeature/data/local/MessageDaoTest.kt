package krio.systemdesign.chatapp.crossfeature.data.local

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import krio.systemdesign.chatapp.crossfeature.data.messageEntity
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageDaoTest {

    @Test
    fun `an older version doesn't overwrite a newer one`() = dataTest {
        val dao = db.messageDao()
        dao.upsertNewer(listOf(messageEntity("c", 1, version = 2, text = "Edited")))

        dao.upsertNewer(listOf(messageEntity("c", 1, version = 1, text = "Original")))

        assertThat(dao.observeWindow("c", 1, 1).first().single().text).isEqualTo("Edited")
    }

    @Test
    fun `an edit of a message not cached is dropped`() = dataTest {
        val dao = db.messageDao()

        dao.updateExistingNewer(listOf(messageEntity("c", 1, version = 2)))

        assertThat(dao.maxSeq("c")).isNull()
    }

    @Test
    fun `an open window stops at the first gap`() = dataTest {
        cacheMessages("c", 1L..3L)
        cacheMessages("c", 5L..6L)

        val seqs = db.messageDao().observeWindow("c", fromSeq = 1, toSeq = null).first().map { it.seq }

        assertThat(seqs).containsExactly(3L, 2L, 1L)
    }

    @Test
    fun `a closed window shows its range, newest first`() = dataTest {
        cacheMessages("c", 1L..10L)

        val seqs = db.messageDao().observeWindow("c", fromSeq = 4, toSeq = 6).first().map { it.seq }

        assertThat(seqs).containsExactly(6L, 5L, 4L)
    }

    @Test
    fun `a gapless run is found within its bounds`() = dataTest {
        cacheMessages("c", 3L..10L)
        val dao = db.messageDao()

        // The run 3..10 starts at 3 and ends at 10; looking only from 5 or up to 8, it goes past the bound.
        assertThat(dao.runStart("c", seq = 9, notBelow = 1)).isEqualTo(3)
        assertThat(dao.runStart("c", seq = 9, notBelow = 5)).isNull()
        assertThat(dao.runEnd("c", seq = 4, notAbove = 20)).isEqualTo(10)
        assertThat(dao.runEnd("c", seq = 4, notAbove = 8)).isNull()
    }

    @Test
    fun `counting tells a cached range from a range with a gap`() = dataTest {
        cacheMessages("c", 1L..3L)
        cacheMessages("c", 5L..6L)

        assertThat(db.messageDao().count("c", 1, 3)).isEqualTo(3)
        assertThat(db.messageDao().count("c", 3, 6)).isEqualTo(3)
        assertThat(db.messageDao().isCached("c", 4)).isEqualTo(false)
        assertThat(db.messageDao().observeWindow("c", 5, 5).first().firstOrNull()).isNotNull().prop(MessageEntity::seq)
            .isEqualTo(5L)
    }
}
