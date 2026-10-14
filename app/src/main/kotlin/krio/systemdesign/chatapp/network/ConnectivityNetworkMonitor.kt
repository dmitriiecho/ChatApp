package krio.systemdesign.chatapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import krio.systemdesign.chatapp.core.network.NetworkMonitor
import org.koin.core.annotation.Singleton

// Whether the device has a default network, from ConnectivityManager. Switching from Wi-Fi to mobile data loses
// the old network before the new one comes: a short false, which only makes the socket wait for the new one.
@Singleton
internal class ConnectivityNetworkMonitor(private val context: Context) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(manager.activeNetwork != null)
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .conflate()
}
