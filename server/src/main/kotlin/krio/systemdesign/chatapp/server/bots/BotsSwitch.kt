package krio.systemdesign.chatapp.server.bots

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import krio.systemdesign.chatapp.contract.RealtimeEventDto
import krio.systemdesign.chatapp.server.db.Database
import krio.systemdesign.chatapp.server.events.EventBus

// Whether the bots act, for the whole server: anyone signed in may turn them off for everyone. Kept in the database,
// so it outlives a restart; on by default.
class BotsSwitch private constructor(
    private val db: Database,
    private val events: EventBus,
    enabled: Boolean,
) {
    private val _isEnabled = MutableStateFlow(enabled)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // Two requests at once would otherwise both see the old value, and the second would send no event.
    private val mutex = Mutex()

    suspend fun set(enabled: Boolean) = mutex.withLock {
        if (enabled == _isEnabled.value) return@withLock
        db.transaction { update("INSERT OR REPLACE INTO settings(key, value) VALUES (?, ?)", KEY, enabled.toString()) }
        _isEnabled.value = enabled
        events.publishToAll(RealtimeEventDto.BotsChanged(enabled))
    }

    companion object {
        private const val KEY = "botsEnabled"

        suspend fun load(
            db: Database,
            events: EventBus,
        ): BotsSwitch {
            val stored = db.transaction {
                queryOne("SELECT value FROM settings WHERE key = ?", KEY) { it.getString(1) }
            }
            return BotsSwitch(db, events, enabled = stored?.toBooleanStrict() ?: true)
        }
    }
}
