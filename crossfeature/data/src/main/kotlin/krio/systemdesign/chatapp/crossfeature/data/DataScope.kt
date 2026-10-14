package krio.systemdesign.chatapp.crossfeature.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Singleton

// Work that outlives any screen, as long as the process does: the current session kept in memory.
@Singleton
internal class DataScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
