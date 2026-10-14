package krio.systemdesign.chatapp.crossfeature.data.repository

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import java.io.IOException
import kotlin.test.Test
import krio.systemdesign.chatapp.core.network.apiRequest
import krio.systemdesign.chatapp.core.network.apiSample
import krio.systemdesign.chatapp.core.network.apiSampleText
import krio.systemdesign.chatapp.core.network.networkJson
import krio.systemdesign.chatapp.crossfeature.data.dataTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BotsRepositoryImplTest {

    @Test
    fun `the switch is unknown until the server answers`() = dataTest {
        assertThat(bots.isEnabled.value).isEqualTo(null)

        server.enqueue(body = apiSampleText("bots.json"))
        bots.refresh()

        val request = server.takeRequest()
        assertThat("${request.method} ${request.target}").isEqualTo(apiRequest("bots"))
        assertThat(bots.isEnabled.value).isEqualTo(true)
    }

    @Test
    fun `turning the bots off sends the sample's request and keeps the server's answer`() = dataTest {
        server.enqueue(body = apiSampleText("bots-response.json"))

        bots.setEnabled(false)

        val request = server.takeRequest()
        assertThat("${request.method} ${request.target}").isEqualTo(apiRequest("set-bots"))
        assertThat(networkJson.parseToJsonElement(request.body)).isEqualTo(apiSample("bots-request.json"))
        assertThat(bots.isEnabled.value).isEqualTo(false)
    }

    @Test
    fun `without the network the switch stays as it was`() = dataTest {
        bots.onChanged(true)
        server.shutDown()

        assertFailure { bots.setEnabled(false) }.isInstanceOf<IOException>()
        assertThat(bots.isEnabled.value).isEqualTo(true)
    }
}
