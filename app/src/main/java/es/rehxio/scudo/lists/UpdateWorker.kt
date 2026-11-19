package es.rehxio.scudo.lists

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import es.rehxio.scudo.update.AppUpdater
import java.util.concurrent.TimeUnit

/** Una vez al día, con conexión y batería suficiente: actualiza las listas y mira si hay versión nueva de la app. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ListRepository.get(applicationContext).updateAll()
        if (AppUpdater.isAutoCheck(applicationContext)) {
            runCatching { AppUpdater.check(applicationContext, notify = true) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("update-lists", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
