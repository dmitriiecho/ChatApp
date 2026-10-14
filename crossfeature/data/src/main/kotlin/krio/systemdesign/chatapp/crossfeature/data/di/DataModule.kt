package krio.systemdesign.chatapp.crossfeature.data.di

import android.content.Context
import androidx.room3.Room
import krio.systemdesign.chatapp.crossfeature.data.local.ChatDatabase
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

// @Configuration: the app's graph picks the module up on its own.
@Module
@Configuration
@ComponentScan("krio.systemdesign.chatapp.crossfeature.data")
class DataModule {

    // A cache: a new schema drops it instead of migrating, and everything is loaded again; the sign-in goes too.
    @Singleton
    internal fun database(context: Context): ChatDatabase = Room.databaseBuilder<ChatDatabase>(context, DATABASE_NAME)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    private companion object {
        const val DATABASE_NAME = "chat.db"
    }
}
