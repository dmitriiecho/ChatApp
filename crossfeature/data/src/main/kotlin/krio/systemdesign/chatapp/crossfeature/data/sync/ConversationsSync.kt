package krio.systemdesign.chatapp.crossfeature.data.sync

import androidx.room3.withWriteTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import krio.systemdesign.chatapp.contract.ConversationChangesDto
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.ConversationListMetaEntity
import krio.systemdesign.chatapp.crossfeature.data.local.removeConversations
import krio.systemdesign.chatapp.crossfeature.data.remote.ConversationsApi
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import krio.systemdesign.chatapp.crossfeature.data.repository.CONVERSATIONS_PAGE_SIZE
import krio.systemdesign.chatapp.crossfeature.data.repository.boundaryBelow
import krio.systemdesign.chatapp.crossfeature.data.repository.hasMoreBelow
import org.koin.core.annotation.Singleton

// The chat list's sync by its token (server/README.md, "Sync logs"). Run by RealtimeHandler.onReconnected on
// every connection, the first included: it is the app's job, not the list screen's, so chats sync even when the app
// opens straight in a chat.
//   no token (first start)   → the first token, then the first page
//   a token                  → the changes after it, applied, and the next token, until there are no more
//   resetRequired            → the token is too old: the list starts over from the first page
// The changes and the new token are written in one transaction: if anything fails in between, neither is, and
// the next sync asks again from the old token. The token never moves past changes that weren't applied.
@Singleton
internal class ConversationsSync(
    private val api: ConversationsApi,
    private val db: ChatDatabase,
) {
    private val conversationDao = db.conversationDao()
    private val metaDao = db.conversationListMetaDao()

    // A quick background and foreground: the old connection may still be syncing when the new one starts.
    // The second sync waits for the first.
    private val mutex = Mutex()

    suspend fun sync() = mutex.withLock {
        var token = metaDao.get()?.syncToken
        if (token == null) {
            loadFirstPage()
            return@withLock
        }
        do {
            val changes: ConversationChangesDto = api.changes(sinceToken = token, limit = LIMIT)
            if (changes.resetRequired) {
                loadFirstPage()
                return@withLock
            }
            db.withWriteTransaction {
                // Chats that rose to the top get above the boundary and show in the list; changed ones deep down
                // aren't kept, a page brings them when the user scrolls there. A change the socket brought already
                // comes again here: version keeps it from doing harm.
                conversationDao.upsertIfRelevant(changes.changed.map { it.toEntity() })
                db.removeConversations(changes.removedIds)
                metaDao.upsert(metaDao.get()!!.copy(syncToken = changes.nextToken))
            }
            token = changes.nextToken
        } while (changes.hasMore)
    }

    // The list from scratch: the first token, then the first page, then the boundary.
    // In this order: a chat changed between the two requests comes in the page and again from the log, which is
    // harmless (version). The other way round, a change between the page and the token would be in neither.
    private suspend fun loadFirstPage() {
        val token = api.changes(sinceToken = null, limit = LIMIT).nextToken
        val page = api.latest(limit = CONVERSATIONS_PAGE_SIZE)
        db.withWriteTransaction {
            // The whole cached list may be out of date. Messages stay: they have their own log per chat.
            conversationDao.deleteAll()
            conversationDao.upsertNewer(page.items.map { it.toEntity() })
            metaDao.upsert(
                ConversationListMetaEntity(
                    loadedDownToSortKey = page.boundaryBelow(),
                    hasMore = page.hasMoreBelow,
                    syncToken = token,
                ),
            )
        }
    }

    private companion object {
        const val LIMIT = 200
    }
}
