package krio.systemdesign.chatapp.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.TimeUnit
import krio.systemdesign.chatapp.core.network.AuthHeaders
import krio.systemdesign.chatapp.core.network.ServerConfig
import krio.systemdesign.chatapp.core.network.serverApi
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

// @Configuration: the app's graph picks the module up on its own.
@Module
@Configuration
@ComponentScan("krio.systemdesign.chatapp.core.network")
class NetworkModule {

    // One client for the app: its connection pool and threads are shared by every request and the events socket.
    @Singleton
    fun httpClient(authHeaders: AuthHeaders): HttpClient = HttpClient(OkHttp) {
        engine {
            // The events socket's keep-alive. With OkHttp the ping is OkHttp's own: the WebSockets plugin's
            // pingInterval doesn't apply to this engine.
            config { pingInterval(PING_SECONDS, TimeUnit.SECONDS) }
        }
        serverApi(ServerConfig.BASE_URL, authHeaders)
        // No request timeout: it would also end the events socket, which stays open for as long as the app is on
        // screen. A dead connection is found by the ping and the socket timeout instead.
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }
    }

    private companion object {
        const val PING_SECONDS = 15L
        const val TIMEOUT_MILLIS = 30_000L
    }
}
