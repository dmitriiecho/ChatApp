package krio.systemdesign.chatapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import krio.systemdesign.chatapp.crossfeature.data.sync.OutboxSender
import org.koin.android.annotation.KoinWorker

// Sends the outbox. Runs only with a network (WorkManagerOutboxScheduler) and after a failure again later, with
// a growing pause, also when the app was closed meanwhile.
@KoinWorker
internal class OutboxWorker(
    context: Context,
    params: WorkerParameters,
    private val sender: OutboxSender,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        sender.drain()
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    }
}
