package com.kitakkun.jetwhale.plugins.background.agent

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkSourceInfo
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
import kotlinx.coroutines.flow.Flow

actual fun BackgroundWorkSource.Companion.platformDefaults(): List<BackgroundWorkSource> {
    val context = currentApplicationOrNull() ?: return emptyList()
    return listOf(JobSchedulerSource(context), AlarmClockSource(context), RunningServicesSource(context))
}

/** WorkManager schedules its own work through this service, so its jobs are WorkManager's to manage. */
private const val WORK_MANAGER_JOB_SERVICE = "androidx.work.impl.background.systemjob.SystemJobService"

/** The extra WorkManager stores its work id under in the jobs it schedules. */
private const val WORK_MANAGER_JOB_EXTRA = "EXTRA_WORK_SPEC_ID"

private class JobSchedulerSource(private val context: Context) : BackgroundWorkSource {
    private val scheduler = context.getSystemService(JobScheduler::class.java)

    override val info = WorkSourceInfo(
        name = "JobScheduler",
        available = scheduler != null,
        unavailableReason = if (scheduler == null) "JobScheduler is not available on this device" else null,
        supportsCancelByTag = false,
        supportsCancelByUniqueName = false,
    )

    override fun observe(): Flow<List<BackgroundWorkItem>> = pollWork(POLL_INTERVAL_MILLIS) {
        pendingJobs().map { (namespace, job) -> toItem(namespace, job) }
    }

    /**
     * Every pending job with the namespace it was scheduled in. Since API 34 WorkManager schedules
     * into a namespace of its own, which [JobScheduler.getAllPendingJobs] does not include.
     */
    private fun pendingJobs(): List<Pair<String?, JobInfo>> {
        val scheduler = scheduler ?: return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return scheduler.allPendingJobs.map { null to it }
        return scheduler.pendingJobsInAllNamespaces.flatMap { (namespace, jobs) -> jobs.map { namespace to it } }
    }

    private fun toItem(namespace: String?, job: JobInfo): BackgroundWorkItem {
        val managedByWorkManager = job.service.className == WORK_MANAGER_JOB_SERVICE
        val workSpecId = job.extras.getString(WORK_MANAGER_JOB_EXTRA)
        val namespaceOption = namespace?.let { "-n $it " }.orEmpty()
        return BackgroundWorkItem(
            source = info.name,
            id = namespace?.let { "$it$NAMESPACE_SEPARATOR${job.id}" } ?: job.id.toString(),
            name = job.service.className,
            state = WorkState.Scheduled,
            tags = emptyList(),
            uniqueName = null,
            runAttemptCount = null,
            constraints = jobConstraints(job),
            nextRunEpochMillis = null,
            periodMillis = job.intervalMillis.takeIf { job.isPeriodic },
            flexMillis = job.flexMillis.takeIf { job.isPeriodic },
            progress = emptyMap(),
            output = emptyMap(),
            stopReason = null,
            details = buildMap {
                put("Persisted across reboots", job.isPersisted.toString())
                if (job.minLatencyMillis > 0) put("Minimum latency", "${job.minLatencyMillis} ms")
                if (job.maxExecutionDelayMillis > 0) put("Deadline", "${job.maxExecutionDelayMillis} ms")
                namespace?.let { put("Namespace", it) }
                if (managedByWorkManager) put("Managed by", "WorkManager")
                workSpecId?.let { put("WorkManager work id", it) }
            },
            // Cancelling WorkManager's job behind its back leaves WorkManager believing it is
            // scheduled; it has to be cancelled through WorkManager.
            canCancel = !managedByWorkManager,
            canRunNow = false,
            runNowHint = "adb shell cmd jobscheduler run -f $namespaceOption${context.packageName} ${job.id}",
        )
    }

    override suspend fun cancel(target: CancelTarget): String {
        val itemId = (target as? CancelTarget.ById)?.id ?: throw IllegalArgumentException("JobScheduler jobs are cancelled by their id")
        val namespace = itemId.substringBeforeLast(NAMESPACE_SEPARATOR, missingDelimiterValue = "").ifEmpty { null }
        val jobId = itemId.substringAfterLast(NAMESPACE_SEPARATOR).toIntOrNull() ?: throw IllegalArgumentException("'$itemId' is not a job id")
        val scoped = scheduler?.let { if (namespace != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) it.forNamespace(namespace) else it }
        val job = scoped?.getPendingJob(jobId) ?: throw IllegalArgumentException("no pending job has id $itemId")
        require(job.service.className != WORK_MANAGER_JOB_SERVICE) { "job $itemId belongs to WorkManager; cancel the WorkManager work instead" }
        scoped.cancel(jobId)
        return "Cancelled job $itemId."
    }

    override suspend fun runNow(id: String): String = throw UnsupportedOperationException("an app cannot force its own job to run; use the adb command shown for it")
}

/** Joins a job's namespace and numeric id into one item id; namespaces cannot contain it. */
private const val NAMESPACE_SEPARATOR = "/"

private fun jobConstraints(job: JobInfo): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && job.requiredNetwork != null) add("network required")
    if (job.isRequireCharging) add("charging")
    if (job.isRequireDeviceIdle) add("device idle")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (job.isRequireBatteryNotLow) add("battery not low")
        if (job.isRequireStorageNotLow) add("storage not low")
    }
}

/**
 * The next alarm clock, which is the only alarm Android lets an app read back. Ordinary alarms set
 * with AlarmManager.set* cannot be listed from inside the app; `adb shell dumpsys alarm` shows them.
 */
private class AlarmClockSource(private val context: Context) : BackgroundWorkSource {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override val info = WorkSourceInfo(
        name = "Alarm clock",
        available = alarmManager != null,
        unavailableReason = if (alarmManager == null) "AlarmManager is not available on this device" else null,
        supportsCancelByTag = false,
        supportsCancelByUniqueName = false,
    )

    override fun observe(): Flow<List<BackgroundWorkItem>> = pollWork(POLL_INTERVAL_MILLIS) {
        val next = alarmManager?.nextAlarmClock
        // The next alarm clock is the device's, not the app's; it is shown only when the app set it.
        if (next == null || next.showIntent?.creatorPackage != context.packageName) {
            emptyList()
        } else {
            listOf(
                BackgroundWorkItem(
                    source = info.name,
                    id = "next",
                    name = "Next alarm clock",
                    state = WorkState.Scheduled,
                    tags = emptyList(),
                    uniqueName = null,
                    runAttemptCount = null,
                    constraints = emptyList(),
                    nextRunEpochMillis = next.triggerTime,
                    periodMillis = null,
                    flexMillis = null,
                    progress = emptyMap(),
                    output = emptyMap(),
                    stopReason = null,
                    details = mapOf("Other alarms" to "adb shell dumpsys alarm | grep -A4 ${context.packageName}"),
                    canCancel = false,
                    canRunNow = false,
                    runNowHint = null,
                ),
            )
        }
    }

    override suspend fun cancel(target: CancelTarget): String = throw UnsupportedOperationException("an alarm can only be cancelled with the PendingIntent that set it, which the app holds")

    override suspend fun runNow(id: String): String = throw UnsupportedOperationException("alarms cannot be triggered early from inside the app")
}

/** The app's own services that are running now; Android lists only the caller's own since API 26. */
private class RunningServicesSource(private val context: Context) : BackgroundWorkSource {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    override val info = WorkSourceInfo(
        name = "Services",
        available = activityManager != null,
        unavailableReason = if (activityManager == null) "ActivityManager is not available on this device" else null,
        supportsCancelByTag = false,
        supportsCancelByUniqueName = false,
    )

    override fun observe(): Flow<List<BackgroundWorkItem>> = pollWork(POLL_INTERVAL_MILLIS) {
        runningServices().map { service ->
            val startedAt = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - service.activeSince)
            BackgroundWorkItem(
                source = info.name,
                id = service.service.flattenToString(),
                name = service.service.className,
                state = WorkState.Running,
                tags = emptyList(),
                uniqueName = null,
                runAttemptCount = null,
                constraints = emptyList(),
                nextRunEpochMillis = null,
                periodMillis = null,
                flexMillis = null,
                progress = emptyMap(),
                output = emptyMap(),
                stopReason = null,
                details = mapOf("Foreground" to service.foreground.toString(), "Running since (epoch ms)" to startedAt.toString()),
                canCancel = true,
                canRunNow = false,
                runNowHint = null,
            )
        }
    }

    // getRunningServices is deprecated for reading other apps' services, but it is still the way to
    // list the caller's own, which is all it returns since API 26.
    @Suppress("DEPRECATION")
    private fun runningServices(): List<ActivityManager.RunningServiceInfo> = activityManager?.getRunningServices(Int.MAX_VALUE).orEmpty().filter { it.uid == Process.myUid() }

    override suspend fun cancel(target: CancelTarget): String {
        val id = (target as? CancelTarget.ById)?.id ?: throw IllegalArgumentException("services are stopped by their id")
        val component = ComponentName.unflattenFromString(id) ?: throw IllegalArgumentException("'$id' is not a service component")
        val stopped = context.stopService(Intent().setComponent(component))
        return if (stopped) "Stopped ${component.className}." else "${component.className} was not running."
    }

    override suspend fun runNow(id: String): String = throw UnsupportedOperationException("a running service is already running")
}

/**
 * The app's [Application], reached without the app passing a Context in: the hidden
 * `ActivityThread.currentApplication()` is what the agent runtime uses for the same purpose.
 */
private fun currentApplicationOrNull(): Context? = try {
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
} catch (_: Exception) {
    null
}
