package krio.systemdesign.chatapp.crossfeature.data.sync

import androidx.room3.withWriteTransaction
import krio.systemdesign.chatapp.crossfeature.data.Clock
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.remote.ChatApi
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import org.koin.core.annotation.Singleton

// The sync of one chat's edits and deletions by its token. New messages aren't in it: the window loads them.
// Run by MessagesRepository.refreshLatest when a chat opens, and after a reconnect for a few recently opened chats.
//   no token (first opening) → the chat's first token, no changes
//   a token                  → the edits after it, applied, and the next token, until there are no more
//   resetRequired            → the token is too old: the chat's cache is dropped
// Per chat rather than one log, so the work follows the chats the user opens, not the activity in all of them.
@Singleton
internal class MessageChangesSync(
    private val api: ChatApi,
    private val db: ChatDatabase,
    private val clock: Clock,
) {
    private val messageDao = db.messageDao()
    private val stateDao = db.chatSyncStateDao()

    suspend fun sync(conversationId: String) {
        do {
            val token = stateDao.get(conversationId)?.messagesSyncToken
            val changes = api.changes(conversationId, sinceToken = token, limit = LIMIT)
            db.withWriteTransaction {
                if (changes.resetRequired) {
                    // What changed is unknown, so the chat's cache goes. An open window empties until the latest
                    // page comes, which refreshLatest loads right after this sync.
                    messageDao.deleteByConversations(listOf(conversationId))
                } else {
                    // Edits of messages not cached aren't needed.
                    messageDao.updateExistingNewer(changes.changed.map { it.toEntity() })
                }
                // The token in the same transaction as the changes.
                stateDao.ensureExists(conversationId)
                stateDao.setToken(conversationId, changes.nextToken)
            }
        } while (changes.hasMore && !changes.resetRequired)
    }

    // When the chat was opened: after a reconnect the recently opened ones are synced.
    suspend fun markOpened(conversationId: String) = db.withWriteTransaction {
        stateDao.ensureExists(conversationId)
        stateDao.setLastOpened(conversationId, clock.nowMillis())
    }

    // So recently opened chats open offline later with their current text.
    suspend fun syncRecentlyOpened(limit: Int) {
        stateDao.recentlyOpened(limit).forEach { sync(it) }
    }

    private companion object {
        const val LIMIT = 200
    }
}
