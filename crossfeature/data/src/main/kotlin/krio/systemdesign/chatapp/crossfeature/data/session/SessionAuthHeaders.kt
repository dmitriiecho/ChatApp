package krio.systemdesign.chatapp.crossfeature.data.session

import krio.systemdesign.chatapp.contract.ApiHeaders
import krio.systemdesign.chatapp.core.network.AuthHeaders
import krio.systemdesign.chatapp.crossfeature.data.ApiKey
import org.koin.core.annotation.Singleton

// Every request's headers: the app's key and the signed-in user's id.
@Singleton
internal class SessionAuthHeaders(
    private val apiKey: ApiKey,
    private val sessionStore: SessionStore,
) : AuthHeaders {
    override fun headers(): Map<String, String> = buildMap {
        put(ApiHeaders.API_KEY, apiKey.value)
        sessionStore.current.value?.let { put(ApiHeaders.USER_ID, it.userId) }
    }
}
