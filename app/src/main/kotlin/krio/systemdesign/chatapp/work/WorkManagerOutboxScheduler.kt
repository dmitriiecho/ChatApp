package krio.systemdesign.chatapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import krio.systemdesign.chatapp.crossfeature.data.OutboxScheduler
import org.koin.core.annotation.Singleton

// The outbox's sending as one unique work: it waits for a network and survives the app being closed.
@Singleton
internal class WorkManagerOutboxScheduler(private val context: Context) : OutboxScheduler {

    // APPEND_OR_REPLACE, not KEEP: with KEEP a message queued while the running work is finishing would wait for the
    // next reason to send. Appended, a new run follows the current one; a failed or cancelled one is replaced.
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "outbox"
        const val BACKOFF_SECONDS = 10L
    }
}
