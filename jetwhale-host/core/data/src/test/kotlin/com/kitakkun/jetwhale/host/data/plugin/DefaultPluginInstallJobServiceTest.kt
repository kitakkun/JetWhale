package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallJob
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallRequest
import com.kitakkun.jetwhale.host.model.PluginInstallStatus
import com.kitakkun.jetwhale.host.model.isCancellable
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultPluginInstallJobServiceTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File
    private lateinit var provider: AppDataDirectoryProvider
    private lateinit var service: DefaultPluginInstallJobService

    private val coordinates = MavenCoordinates(groupId = "com.example", artifactId = "network", version = "1.3.0", repositoryUrl = "https://example.com/releases")
    private val request = PluginInstallRequest.Maven(coordinates)

    // Each stage of an install waits on its gate, so a test can look at the install while it is there.
    private val pluginDownloadGate = CompletableDeferred<Unit>()
    private val dependencyDownloadGate = CompletableDeferred<Unit>()
    private val loadGate = CompletableDeferred<Unit>()
    private val pluginDownloads = AtomicInteger()
    private val trustService = FakeTrustService(onApprove = { _, _ -> loadGate.await() })

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = File.createTempFile("jetwhale-home-", "").apply {
            delete()
            mkdirs()
        }
        System.setProperty("user.home", tempHome.absolutePath)
        provider = AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList()))
        provider.createAppDataDirectoriesIfNeeded()
        val progressRepository = DefaultPluginInstallProgressRepository()
        val engine = MockEngine { httpRequest ->
            val url = httpRequest.url.toString()
            when {
                "/network/" in url -> {
                    pluginDownloads.incrementAndGet()
                    pluginDownloadGate.await()
                    respond(jarBytes(PluginDependencyManifest.JAR_ENTRY, "com.example:support:2.0.0"))
                }

                "/support/" in url -> {
                    dependencyDownloadGate.await()
                    respond(jarBytes("support", ""))
                }

                "/storage" in url -> respond(jarBytes("storage", ""))

                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        service = DefaultPluginInstallJobService(
            mavenPluginInstallService = MavenPluginInstallService(
                appDataDirectoryProvider = provider,
                mavenArtifactResolver = MavenArtifactResolver(engine),
                pluginTrustService = trustService,
                pluginTrustRepository = FakeTrustRepository(),
                pluginFactoryRepository = FakeFactoryRepository(),
                pluginInstallProgressRepository = progressRepository,
            ),
            pluginInstallProgressRepository = progressRepository,
            hostVersionInfo = HostVersionInfo("0.0.0-test"),
        )
    }

    @AfterTest
    fun cleanup() {
        // Opened first: an install that has started loading its plugin is not cancelled but waited for.
        openAllGates()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { service.cancelAll() } }
        originalUserHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @Test
    fun `an install keeps going after the caller that started it is gone`() = runBlocking {
        val callerScope = CoroutineScope(Dispatchers.Default)
        val caller = callerScope.launch { service.install(request) }
        awaitStatus(awaitJobFor(request).id) { it == PluginInstallStatus.Running(PluginInstallProgress.DownloadingPlugin) }

        caller.cancelAndJoin()
        openAllGates()

        awaitStatus(awaitJobFor(request).id) { it == PluginInstallStatus.Succeeded }
        assertTrue(installedJar().isFile)
    }

    @Test
    fun `a second request for the same plugin joins the install under way`() = runBlocking {
        val first = service.enqueue(request)
        val second = service.enqueue(request)

        assertEquals(first.id, second.id)
        assertEquals(listOf(first.id), service.jobsFlow.value.map(PluginInstallJob::id))
        openAllGates()
        awaitStatus(first.id) { it == PluginInstallStatus.Succeeded }
        assertEquals(1, pluginDownloads.get())
    }

    @Test
    fun `an install reports each stage, while the next one waits its turn`() = runBlocking {
        val job = service.enqueue(request)
        val next = service.enqueue(PluginInstallRequest.Maven(coordinates.copy(artifactId = "storage")))

        awaitStatus(job.id) { it == PluginInstallStatus.Running(PluginInstallProgress.DownloadingPlugin) }
        assertEquals(PluginInstallStatus.Queued, statusOf(next.id))
        pluginDownloadGate.complete(Unit)
        awaitStatus(job.id) { it == PluginInstallStatus.Running(PluginInstallProgress.DownloadingDependencies(completed = 0, total = 1)) }
        dependencyDownloadGate.complete(Unit)
        awaitStatus(job.id) { it == PluginInstallStatus.Running(PluginInstallProgress.LoadingPlugin) }
        assertEquals(PluginInstallStatus.Queued, statusOf(next.id))
        loadGate.complete(Unit)

        awaitStatus(job.id) { it == PluginInstallStatus.Succeeded }
        awaitStatus(next.id) { it == PluginInstallStatus.Succeeded }
    }

    @Test
    fun `installs run in the order they were requested`() = runBlocking {
        loadGate.complete(Unit)
        val requested = (1..5).map { coordinates.copy(artifactId = "storage-$it") }
        val jobs = requested.map { service.enqueue(PluginInstallRequest.Maven(it)) }

        jobs.forEach { job -> awaitStatus(job.id) { it == PluginInstallStatus.Succeeded } }

        assertEquals(requested.map(MavenCoordinates::jarFileName), trustService.approvals.map { (jarPath, _) -> File(jarPath).name })
    }

    @Test
    fun `cancelling an install removes its half-downloaded plugin and the install itself`() = runBlocking {
        pluginDownloadGate.complete(Unit)
        val job = service.enqueue(request)
        awaitStatus(job.id) { it is PluginInstallStatus.Running && it.progress is PluginInstallProgress.DownloadingDependencies }

        service.cancel(job.id)

        withTimeout(TIMEOUT_MILLIS) { service.jobsFlow.first { jobs -> jobs.none { it.id == job.id } } }
        assertEquals(emptyList(), provider.getPluginStagingDirectory().listFiles().orEmpty().toList())
        assertFalse(installedJar().exists())
        assertEquals(emptyList(), trustService.approvals)
    }

    @Test
    fun `a caller waiting on a cancelled install is told it was cancelled`() = runBlocking<Unit> {
        val outcome = async(Dispatchers.Default) { service.install(request) }
        val job = awaitJobFor(request)
        awaitStatus(job.id) { it is PluginInstallStatus.Running }

        service.cancel(job.id)

        assertIs<PluginInstallStatus.Failed>(outcome.await())
    }

    @Test
    fun `an install that has started loading the plugin can no longer be cancelled`() = runBlocking {
        pluginDownloadGate.complete(Unit)
        dependencyDownloadGate.complete(Unit)
        val job = service.enqueue(request)
        awaitStatus(job.id) { it == PluginInstallStatus.Running(PluginInstallProgress.LoadingPlugin) }
        assertFalse(statusOf(job.id).isCancellable)

        service.cancel(job.id)
        loadGate.complete(Unit)

        awaitStatus(job.id) { it == PluginInstallStatus.Succeeded }
        assertTrue(installedJar().isFile)
    }

    @Test
    fun `a failed install stays with its reason until it is retried or dismissed`() = runBlocking {
        val broken = PluginInstallRequest.Maven(coordinates.copy(artifactId = "broken"))
        val failed = service.enqueue(broken)
        awaitStatus(failed.id) { it is PluginInstallStatus.Failed }
        assertTrue((statusOf(failed.id) as PluginInstallStatus.Failed).reason.contains("404"))

        val retried = service.enqueue(broken)
        assertEquals(listOf(retried.id), service.jobsFlow.value.map(PluginInstallJob::id))
        awaitStatus(retried.id) { it is PluginInstallStatus.Failed }

        service.dismiss(retried.id)
        assertEquals(emptyList(), service.jobsFlow.value)
    }

    @Test
    fun `dismissing an install that is still running leaves it alone`() = runBlocking {
        val job = service.enqueue(request)

        service.dismiss(job.id)

        assertEquals(listOf(job.id), service.jobsFlow.value.map(PluginInstallJob::id))
    }

    @Test
    fun `cancelling every install on shutdown returns only once their downloads are removed`() = runBlocking {
        pluginDownloadGate.complete(Unit)
        val job = service.enqueue(request)
        val queued = service.enqueue(PluginInstallRequest.Maven(coordinates.copy(artifactId = "storage")))
        awaitStatus(job.id) { it is PluginInstallStatus.Running && it.progress is PluginInstallProgress.DownloadingDependencies }

        service.cancelAll()

        assertEquals(emptyList(), service.jobsFlow.value.filter { it.id == job.id || it.id == queued.id })
        assertEquals(emptyList(), provider.getPluginStagingDirectory().listFiles().orEmpty().toList())
        assertFalse(installedJar().exists())
    }

    private fun openAllGates() {
        pluginDownloadGate.complete(Unit)
        dependencyDownloadGate.complete(Unit)
        loadGate.complete(Unit)
    }

    private fun installedJar(): File = File(provider.getPluginDirectory(), coordinates.jarFileName())

    private fun statusOf(jobId: String): PluginInstallStatus = service.jobsFlow.value.first { it.id == jobId }.status

    private suspend fun awaitJobFor(request: PluginInstallRequest): PluginInstallJob = withTimeout(TIMEOUT_MILLIS) {
        service.jobsFlow.first { jobs -> jobs.any { it.request == request } }.first { it.request == request }
    }

    private suspend fun awaitStatus(jobId: String, predicate: (PluginInstallStatus) -> Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            service.jobsFlow.first { jobs -> jobs.any { it.id == jobId && predicate(it.status) } }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
