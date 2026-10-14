package krio.systemdesign.chatapp.crossfeature.data.repository

import androidx.room3.withWriteTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import krio.systemdesign.chatapp.crossfeature.data.local.toDomain
import krio.systemdesign.chatapp.crossfeature.data.remote.ConversationsApi
import krio.systemdesign.chatapp.crossfeature.data.remote.toEntity
import krio.systemdesign.chatapp.crossfeature.data.session.SessionStore
import krio.systemdesign.chatapp.crossfeature.domain.model.ConversationsSlice
import krio.systemdesign.chatapp.crossfeature.domain.repository.ConversationsRepository
import org.koin.core.annotation.Singleton

// The chat list: a window on the cache and loading pages down. Its sync with the server is ConversationsSync,
// which runs on every connection (RealtimeHandler.onReconnected), not from the screen.
@Singleton
internal class ConversationsRepositoryImpl(
    private val api: ConversationsApi,
    private val db: ChatDatabase,
    private val session: SessionStore,
) : ConversationsRepository {

    private val conversationDao = db.conversationDao()
    private val metaDao = db.conversationListMetaDao()

    // Two loads at once aren't needed: the second waits for the first and then finds enough chats.
    private val mutex = Mutex()

    // One chat more than asked for: if it is there, the cache has more below the window, without a COUNT query.
    override fun observeConversations(
        offset: Int,
        count: Int,
    ): Flow<ConversationsSlice> = combine(
        conversationDao.observeLoaded(offset = offset, limit = count + 1),
        metaDao.observe(),
    ) { rows, meta ->
        ConversationsSlice(
            items = rows.take(count).map { it.toDomain(session.current.value?.userId) },
            // More below in the cache, or on the server.
            hasMore = rows.size > count || meta?.hasMore == true,
            isSynced = meta != null,
        )
    }

    // Page after page until the cache has count chats or the server says there is nothing below. With enough
    // cached already (scrolled this far before), no request at all.
    override suspend fun ensureLoaded(count: Int) = mutex.withLock {
        while (conversationDao.countLoaded() < count) {
            // No first page yet: the sync loads it.
            val meta = metaDao.get() ?: return@withLock
            if (!meta.hasMore) return@withLock
            loadNextPage(boundary = meta.loadedDownToSortKey)
        }
    }

    // The next page: everything below the boundary, which then moves down.
    private suspend fun loadNextPage(boundary: Long) {
        val page = api.before(beforeSortKey = boundary, limit = CONVERSATIONS_PAGE_SIZE)
        db.withWriteTransaction {
            // While the request was out, the sync may have started the list over (resetRequired): the boundary is
            // different and this page is from the old list. It is dropped, or the list would get a gap; the loop in
            // ensureLoaded tries again.
            val meta = metaDao.get() ?: return@withWriteTransaction
            if (meta.loadedDownToSortKey != boundary) return@withWriteTransaction

            conversationDao.upsertNewer(page.items.map { it.toEntity() })
            // The meta read inside the transaction: the sync may have moved the token meanwhile.
            metaDao.upsert(meta.copy(loadedDownToSortKey = page.boundaryBelow(), hasMore = page.hasMoreBelow))
        }
    }
}
