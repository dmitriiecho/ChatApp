package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// The app's notes on each chat opened on this device, made at the first opening.
// messagesSyncToken: the chat's edit log token, null until its first sync. lastOpenedAtMillis: picks the recently
// opened chats whose edits are synced after a reconnect, so they open offline with current text.
// A table of its own, not columns of conversations: a chat's row is replaced by the server's data, which knows
// nothing of these tokens, so every update would erase them.
@Entity(tableName = "chat_sync_state", indices = [Index("lastOpenedAtMillis")])
internal data class ChatSyncStateEntity(
    @PrimaryKey val conversationId: String,
    val messagesSyncToken: String?,
    val lastOpenedAtMillis: Long,
)
