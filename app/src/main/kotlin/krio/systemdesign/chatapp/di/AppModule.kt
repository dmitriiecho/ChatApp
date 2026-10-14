package krio.systemdesign.chatapp.di

import android.content.Context
import android.content.pm.ApplicationInfo
import krio.systemdesign.chatapp.BuildConfig
import krio.systemdesign.chatapp.core.mvi.StoreFactory
import krio.systemdesign.chatapp.crossfeature.data.ApiKey
import krio.systemdesign.chatapp.logging.LogcatStoreLogger
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

// Scans the app's own subpackages only: the app's package holds every module's package, and a scan of it would
// declare all their definitions a second time.
@Module
@Configuration
@ComponentScan(
    "krio.systemdesign.chatapp.network",
    "krio.systemdesign.chatapp.session",
    "krio.systemdesign.chatapp.work",
)
class AppModule {

    // Every screen's Store logs to Logcat in a debuggable build only.
    @Singleton
    fun storeFactory(context: Context): StoreFactory {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return StoreFactory(logger = if (isDebuggable) LogcatStoreLogger else null)
    }

    @Singleton
    fun apiKey(): ApiKey = ApiKey(BuildConfig.API_KEY)
}
