package krio.systemdesign.chatapp.crossfeature.data.session

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import krio.systemdesign.chatapp.crossfeature.data.ANNA
import krio.systemdesign.chatapp.crossfeature.data.API_KEY
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryImplTest {

    @Test
    fun `requests carry the key and the signed-in user`() = dataTest {
        assertThat(authHeaders.headers()).isEqualTo(mapOf("X-Api-Key" to API_KEY, "X-User-Id" to ANNA))
    }

    @Test
    fun `signing out stops the work and drops the cache`() = dataTest {
        cacheMessages("c", 1L..3L)
        outbox.enqueue("c", "Unsent")

        session.logout()

        assertThat(sessionWork.stopped).isEqualTo(1)
        assertThat(session.session.first()).isNull()
        assertThat(db.messageDao().maxSeq("c")).isNull()
        assertThat(db.outboxDao().oldest()).isNull()
    }
}
