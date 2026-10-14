package krio.systemdesign.chatapp

import android.app.Application
import krio.systemdesign.chatapp.crossfeature.domain.repository.SessionRepository
import krio.systemdesign.chatapp.session.RealtimeRunner
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

// The Koin compiler plugin builds the app's graph from every @Configuration module of the app's dependencies
// and checks it while compiling: a missing dependency fails the build.
@KoinApplication
class ChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin<ChatApp> {
            androidContext(this@ChatApp)
            // WorkManager starts here, with Koin's factory, so the outbox worker gets its dependencies.
            workManagerFactory()
        }
        get<RealtimeRunner>().start(get<SessionRepository>().session)
    }
}
