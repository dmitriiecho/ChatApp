package krio.systemdesign.chatapp.core.network

import kotlinx.coroutines.flow.Flow

// Whether the device has a network now; the platform tells (ConnectivityManager on Android, in :app).
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
