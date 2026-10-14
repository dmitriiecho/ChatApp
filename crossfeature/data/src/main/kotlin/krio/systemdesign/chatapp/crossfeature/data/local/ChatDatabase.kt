package krio.systemdesign.chatapp.crossfeature.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction

// The cache of the signed-in user's chats: the only source the screens read. Everything in it can be loaded again,
// so a schema change drops it (fallbackToDestructiveMigration in DataModule) instead of migrating.
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ConversationListMetaEntity::class,
        ChatSyncStateEntity::class,
        OutgoingMessageEntity::class,
        SessionEntity::class,
    ],
    version = 1,
)
internal abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun conversationDao(): ConversationDao

    abstract fun conversationListMetaDao(): ConversationListMetaDao

    abstract fun chatSyncStateDao(): ChatSyncStateDao

    abstract fun outboxDao(): OutboxDao

    abstract fun sessionDao(): SessionDao
}

// The user left a chat or was removed from it: the chat and everything that goes with it.
internal suspend fun ChatDatabase.removeConversations(conversationIds: List<String>) {
    if (conversationIds.isEmpty()) return
    withWriteTransaction {
        conversationDao().deleteByIds(conversationIds)
        messageDao().deleteByConversations(conversationIds)
        chatSyncStateDao().deleteByConversations(conversationIds)
    }
}
