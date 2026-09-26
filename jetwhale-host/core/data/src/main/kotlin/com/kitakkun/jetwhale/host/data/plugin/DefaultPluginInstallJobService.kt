package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallJobService
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import com.kitakkun.jetwhale.host.model.isActive
import com.kitakkun.jetwhale.host.model.isCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstallJobService(
    private val mavenPluginInstallService: MavenPluginInstallService,
    private val pluginInstallProgressRepository: PluginInstallProgressRepository,
    private val hostVersionInfo: HostVersionInfo,
) : PluginInstallJobService {
    // Lives as long as the host, not a screen: this is what keeps an install going after the screen
    // that started it closes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Installs share the staging directory and the single progress slot, so they run one at a time.
    private val installLock = Mutex()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val outcomes = ConcurrentHashMap<String, CompletableDeferred<PluginInstallStatus>>()

    override val jobsFlow: StateFlow<ImmutableList<PluginInstallJob>>
        field = MutableStateFlow(persistentListOf())

    override fun enqueue(request: PluginInstallRequest): PluginInstallJob = enqueueWithOutcome(request).first

    override suspend fun install(request: PluginInstallRequest): PluginInstallStatus = enqueueWithOutcome(request).second.await()

    /** The job for [request], and its outcome, taken under the same lock that retires finished jobs. */
    private fun enqueueWithOutcome(request: PluginInstallRequest): Pair<PluginInstallJob, CompletableDeferred<PluginInstallStatus>> = synchronized(this) {
        jobsFlow.value.firstOrNull { it.request.key == request.key && it.status.isActive }?.let { active ->
            return active to checkNotNull(outcomes[active.id]) { "no outcome is tracked for install ${active.id}" }
        }
        val job = PluginInstallJob(id = UUID.randomUUID().toString(), request = request, status = PluginInstallStatus.Queued)
        val outcome = CompletableDeferred<PluginInstallStatus>()
        jobsFlow.update { jobs -> (jobs.filterNot { it.request.key == request.key } + job).toPersistentList() }
        outcomes[job.id] = outcome
        // Undispatched, so the install registers itself and takes its place in the lock's queue before
        // this returns: installs then run in the order they were requested.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runningJobs[job.id] = coroutineContext.job
            run(job)
        }
        job to outcome
    }

    override fun cancel(jobId: String) {
        val status = jobsFlow.value.firstOrNull { it.id == jobId }?.status ?: return
        if (status.isCancellable) runningJobs[jobId]?.cancel()
    }

    override fun dismiss(jobId: String) {
        jobsFlow.update { jobs -> jobs.filterNot { it.id == jobId && !it.status.isActive }.toPersistentList() }
    }

    override suspend fun cancelAll() {
        val cancellable = jobsFlow.value.filter { it.status.isCancellable }.mapNotNull { runningJobs[it.id] }
        cancellable.forEach(Job::cancel)
        cancellable.joinAll()
    }

    private suspend fun run(job: PluginInstallJob) {
        try {
            val outcome = installLock.withLock {
                setStatus(job.id, PluginInstallStatus.Running(progress = null))
                try {
                    installWhileReportingProgress(job)
                    PluginInstallStatus.Succeeded
                } catch (e: PluginInstallationException) {
                    PluginInstallStatus.Failed(reason = e.message ?: "the plugin could not be installed")
                }
            }
            synchronized(this) {
                setStatus(job.id, outcome)
                outcomes.remove(job.id)?.complete(outcome)
            }
        } finally {
            runningJobs.remove(job.id)
            synchronized(this) {
                // Still tracked here only when cancelled: nothing to report, so the job goes.
                outcomes.remove(job.id)?.let { outcome ->
                    jobsFlow.update { jobs -> jobs.filterNot { it.id == job.id }.toPersistentList() }
                    // A caller waiting in install() gets an answer, not a cancellation of its own.
                    outcome.complete(PluginInstallStatus.Failed(reason = "the install was cancelled"))
                }
            }
        }
    }

    private suspend fun installWhileReportingProgress(job: PluginInstallJob) = coroutineScope {
        val progress = launch {
            pluginInstallProgressRepository.progressFlow.collect { setStatus(job.id, PluginInstallStatus.Running(it)) }
        }
        try {
            val candidates = when (val request = job.request) {
                is PluginInstallRequest.Official -> request.plugin.installCandidatesFor(hostVersionInfo)
                is PluginInstallRequest.Maven -> listOf(request.coordinates)
            }
            mavenPluginInstallService.installFirstAvailable(candidates)
        } finally {
            progress.cancel()
        }
    }

    private fun setStatus(jobId: String, status: PluginInstallStatus) {
        jobsFlow.update { jobs -> jobs.map { if (it.id == jobId) it.copy(status = status) else it }.toPersistentList() }
    }
}
