package com.kitakkun.jetwhale.demo.shared

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.kitakkun.jetwhale.plugins.background.agent.BackgroundWorkSource
import com.kitakkun.jetwhale.plugins.background.agent.platformDefaults
import com.kitakkun.jetwhale.plugins.background.agent.workmanager.workManager
import java.util.concurrent.TimeUnit

private var demoWorkManager: WorkManager? = null

actual fun demoBackgroundWorkSources(): List<BackgroundWorkSource> = BackgroundWorkSource.platformDefaults() + listOfNotNull(demoWorkManager?.let(BackgroundWorkSource::workManager))

/**
 * Enqueues the work the Background Work plugin shows in the demo: a periodic sync, a one-time
 * upload that waits for an idle, unmetered device (so it stays enqueued), and a report that fails.
 * Unique names keep a relaunch from piling up copies.
 */
fun startDemoBackgroundWork(context: Context) {
    val workManager = WorkManager.getInstance(context)
    demoWorkManager = workManager
    workManager.enqueueUniquePeriodicWork(
        "demo-sync",
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequest.Builder(DemoSyncWorker::class.java, 15, TimeUnit.MINUTES).addTag("sync").build(),
    )
    workManager.enqueueUniqueWork(
        "demo-upload",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequest.Builder(DemoUploadWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresDeviceIdle(true).build())
            .addTag("upload")
            .build(),
    )
    workManager.enqueueUniqueWork(
        "demo-report",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequest.Builder(DemoReportWorker::class.java).addTag("report").build(),
    )
}

class DemoSyncWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success(Data.Builder().putLong("syncedAt", System.currentTimeMillis()).build())
}

class DemoUploadWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.success()
}

class DemoReportWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = Result.failure(Data.Builder().putString("reason", "the demo report always fails").build())
}
