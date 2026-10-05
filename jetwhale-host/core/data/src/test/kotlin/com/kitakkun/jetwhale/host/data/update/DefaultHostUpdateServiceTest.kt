package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.HostReleaseSource
import com.kitakkun.jetwhale.host.model.HostUpdateFailure
import com.kitakkun.jetwhale.host.model.HostUpdateState
import com.kitakkun.jetwhale.host.model.HostUpdateStatus
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.SetAsideHostVersion
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostRuntimeRequirements
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostJarName
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DefaultHostUpdateServiceTest {
    private val hostDirectory: Path = Files.createTempDirectory("host-updates").resolve("host")
    private val versions = HostVersionsDirectory(hostDirectory)
    private val requests = CopyOnWriteArrayList<String>()
    private val responses = mutableMapOf<String, suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>()
    private val bodyWriters = CoroutineScope(Dispatchers.IO)

    @AfterTest
    fun stopBodyWriters() {
        bodyWriters.cancel()
    }

    @Test
    fun `offers the newest release above the installed versions that carries metadata`() = runBlocking {
        serveReleases(
            release("1.0.0-alpha12"),
            release("1.0.0-alpha14"),
            release("1.0.0-alpha15"),
            release("1.0.0-alpha16", draft = true),
            release("1.0.0-alpha17-SNAPSHOT"),
            release("1.0.0-alpha18", withMetadata = false),
        )

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.Available("1.0.0-alpha15", jarBytes("1.0.0-alpha15").size.toLong()), service.stateFlow.value.status)
    }

    @Test
    fun `is up to date when nothing newer carries metadata`() = runBlocking {
        serveReleases(release("1.0.0-alpha12"), release("1.0.0-alpha13"), release("1.0.0-alpha14", withMetadata = false))

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.UpToDate, service.stateFlow.value.status)
    }

    @Test
    fun `offers a newer installed version as a restart`() = runBlocking {
        install("1.0.0-alpha14")
        serveReleases(release("1.0.0-alpha14"))

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.ReadyToRestart("1.0.0-alpha14"), service.stateFlow.value.status)
    }

    @Test
    fun `offers a set-aside version as a retry, never as a download`() = runBlocking {
        install("1.0.0-alpha14")
        versions.writeLauncherState(LauncherState(completedStarts = emptySet(), setAside = setOf("1.0.0-alpha14")))
        serveReleases(release("1.0.0-alpha14"))

        val service = service()
        service.check()

        assertEquals(
            HostUpdateState(HostUpdateStatus.UpToDate, SetAsideHostVersion("1.0.0-alpha14", versions.hostLog("1.0.0-alpha14")), restartFailed = false),
            service.stateFlow.value,
        )
    }

    @Test
    fun `says that a release this launcher cannot run needs a new installer`() = runBlocking {
        serveReleases(release("1.0.0-alpha14", edit = { it.copy(launcherContract = 2) }))

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.NeedsNewInstaller("1.0.0-alpha14"), service.stateFlow.value.status)
    }

    @Test
    fun `says that a release has no build for this machine`() = runBlocking {
        serveReleases(release("1.0.0-alpha14", edit = { it.copy(platforms = emptyMap()) }))

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.NoBuildForThisComputer("1.0.0-alpha14"), service.stateFlow.value.status)
    }

    @Test
    fun `reports a used-up rate limit`() = runBlocking {
        responses[RELEASES_URL] = {
            respond("{}", HttpStatusCode.Forbidden, headersOf("x-ratelimit-remaining", "0"))
        }

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.CheckFailed(HostUpdateFailure.RateLimited), service.stateFlow.value.status)
    }

    @Test
    fun `reports GitHub's secondary rate limit as a rate limit`() = runBlocking {
        listOf(HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests).forEach { status ->
            responses[RELEASES_URL] = { respond("{}", status, headersOf(HttpHeaders.RetryAfter, "60")) }

            val service = service()
            service.check()

            assertEquals(HostUpdateStatus.CheckFailed(HostUpdateFailure.RateLimited), service.stateFlow.value.status, "$status")
        }
    }

    @Test
    fun `reports an answer it cannot use`() = runBlocking {
        responses[RELEASES_URL] = { respondError(HttpStatusCode.InternalServerError) }

        val service = service()
        service.check()

        assertEquals(HostUpdateStatus.CheckFailed(HostUpdateFailure.UnexpectedResponse(500)), service.stateFlow.value.status)
    }

    @Test
    fun `reports a network failure`() = runBlocking {
        listOf(UnresolvedAddressException(), IOException("no route to host")).forEach { failure ->
            responses[RELEASES_URL] = { throw failure }

            val service = service()
            service.check()

            assertEquals(HostUpdateStatus.CheckFailed(HostUpdateFailure.Unreachable), service.stateFlow.value.status, "$failure")
        }
    }

    @Test
    fun `leaves Checking when the check ends in an error it does not know`() = runBlocking {
        responses[RELEASES_URL] = { throw IllegalStateException("unexpected") }

        val service = service()
        assertFailsWith<IllegalStateException> { service.check() }

        assertEquals(HostUpdateStatus.NotChecked, service.stateFlow.value.status)
    }

    @Test
    fun `downloads, verifies and installs the offer, following the redirect to the asset host`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        responses[jarUrl("1.0.0-alpha14")] = {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, ASSET_HOST_URL))
        }
        responses[ASSET_HOST_URL] = { respond(jarBytes("1.0.0-alpha14")) }

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.ReadyToRestart("1.0.0-alpha14"), outcome)
        assertTrue(hostDirectory.resolve("1.0.0-alpha14").resolve(hostJarName("1.0.0-alpha14", PLATFORM)).exists())
        assertTrue(hostDirectory.resolve("1.0.0-alpha14/release.json").exists())
        assertStagingEmpty()
    }

    @Test
    fun `discards a download that does not match its metadata`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        val tampered = jarBytes("1.0.0-alpha14").copyOf().also { it[0] = 'X'.code.toByte() }
        responses[jarUrl("1.0.0-alpha14")] = { respond(tampered) }

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.Corrupted), outcome)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `discards a truncated download`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        responses[jarUrl("1.0.0-alpha14")] = { respond(jarBytes("1.0.0-alpha14").copyOf(4)) }

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.Corrupted), outcome)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `stops a download that runs past the size its metadata pins`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        responses[jarUrl("1.0.0-alpha14")] = {
            val body = bodyWriters.writer {
                channel.writeFully(jarBytes("1.0.0-alpha14") + "and more".toByteArray())
                channel.flush()
                awaitCancellation()
            }.channel
            respond(body, HttpStatusCode.OK)
        }

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.Corrupted), outcome)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `discards an interrupted download`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        responses[jarUrl("1.0.0-alpha14")] = {
            val body = ByteChannel()
            body.writeFully(jarBytes("1.0.0-alpha14"), 0, 4)
            body.flush()
            body.cancel(IOException("connection reset"))
            respond(body, HttpStatusCode.OK)
        }

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.Unreachable), outcome)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `discards a download whose asset host cannot be resolved`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        responses[jarUrl("1.0.0-alpha14")] = { throw UnresolvedAddressException() }

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.Unreachable), outcome)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `reports a download it could not save on this computer apart from a network failure`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        Files.createDirectories(hostDirectory)
        val host = hostDirectory.toFile()
        Assume.assumeTrue("the file system cannot take write permission away", host.setWritable(false) && !Files.isWritable(hostDirectory))
        try {
            val outcome = checkAndDownload(service())

            assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.CouldNotSave), outcome)
        } finally {
            host.setWritable(true)
        }
    }

    @Test
    fun `ends a download that cannot clear staging as not saved`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        val leftover = versions.staging.resolve("1.0.0-alpha13")
        Files.createDirectories(leftover)
        Files.writeString(leftover.resolve("part.jar"), "part")
        Assume.assumeTrue("the file system cannot take write permission away", leftover.toFile().setWritable(false) && !Files.isWritable(leftover))
        try {
            val outcome = checkAndDownload(service())

            assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.CouldNotSave), outcome)
        } finally {
            leftover.toFile().setWritable(true)
        }
    }

    @Test
    fun `offers the release again after a download ends in an error it does not know`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        val requested = CompletableDeferred<Unit>()
        responses[jarUrl("1.0.0-alpha14")] = {
            requested.complete(Unit)
            throw IllegalStateException("unexpected")
        }
        val service = service()
        service.check()

        service.download()
        withTimeout(10.seconds) { requested.await() }

        val status = withTimeout(10.seconds) { service.stateFlow.first { it.status !is HostUpdateStatus.Downloading }.status }
        assertEquals(HostUpdateStatus.Available("1.0.0-alpha14", jarBytes("1.0.0-alpha14").size.toLong()), status)
        assertStagingEmpty()
    }

    @Test
    fun `deletes every other downloaded version before a download`() = runBlocking {
        install("1.0.0-alpha13")
        install("1.0.0-alpha14")
        versions.writeLauncherState(LauncherState(completedStarts = setOf("1.0.0-alpha13"), setAside = setOf("1.0.0-alpha14")))
        serveReleases(release("1.0.0-alpha15"))

        val outcome = checkAndDownload(service())

        assertEquals(HostUpdateStatus.ReadyToRestart("1.0.0-alpha15"), outcome)
        assertEquals(listOf("1.0.0-alpha15", "1.0.0-alpha13"), versions.installedVersions().map(InstalledHostVersion::name))
    }

    @Test
    fun `stops offering a set-aside version once a download has deleted it, even when the download fails`() = runBlocking {
        install("1.0.0-alpha14")
        versions.writeLauncherState(LauncherState(completedStarts = emptySet(), setAside = setOf("1.0.0-alpha14")))
        serveReleases(release("1.0.0-alpha15"))
        responses[jarUrl("1.0.0-alpha15")] = { respondError(HttpStatusCode.InternalServerError) }
        val service = service()

        val outcome = checkAndDownload(service)

        assertEquals(HostUpdateStatus.DownloadFailed(HostUpdateFailure.UnexpectedResponse(500)), outcome)
        assertEquals(null, service.stateFlow.value.setAside)
    }

    @Test
    fun `offers the release again after a cancelled download`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        val bodyStarted = serveStalledJar("1.0.0-alpha14")
        val service = service()
        service.check()

        service.download()
        withTimeout(10.seconds) { bodyStarted.await() }
        service.cancelDownload()

        val status = withTimeout(10.seconds) { service.stateFlow.first { it.status !is HostUpdateStatus.Downloading }.status }
        assertEquals(HostUpdateStatus.Available("1.0.0-alpha14", jarBytes("1.0.0-alpha14").size.toLong()), status)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `installs nothing when the download is cancelled as it is verified`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        val service = service()
        service.check()
        val cancelOnVerifying = launch(Dispatchers.Unconfined) {
            service.stateFlow.first { it.status is HostUpdateStatus.Verifying }
            service.cancelDownload()
        }

        service.download()
        withTimeout(10.seconds) { cancelOnVerifying.join() }

        val status = withTimeout(10.seconds) { service.stateFlow.first { it.status !is HostUpdateStatus.Verifying }.status }
        assertEquals(HostUpdateStatus.Available("1.0.0-alpha14", jarBytes("1.0.0-alpha14").size.toLong()), status)
        assertEquals(emptyList(), versions.installedVersions())
        assertStagingEmpty()
    }

    @Test
    fun `does not check for updates while a download runs`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        val bodyStarted = serveStalledJar("1.0.0-alpha14")
        val service = service()
        service.check()
        service.download()
        withTimeout(10.seconds) { bodyStarted.await() }

        service.check()

        assertIs<HostUpdateStatus.Downloading>(service.stateFlow.value.status)
        assertEquals(1, requests.count { it == RELEASES_URL })
        service.cancelDownload()
    }

    @Test
    fun `runs one download at a time`() = runBlocking {
        serveReleases(release("1.0.0-alpha14"))
        serveStalledJar("1.0.0-alpha14")
        val service = service()
        service.check()
        service.download()
        val progress = withTimeout(10.seconds) {
            service.stateFlow.first { (it.status as? HostUpdateStatus.Downloading)?.downloadedBytes == 4L }.status
        }

        service.download()

        assertEquals(progress, service.stateFlow.value.status)
        service.cancelDownload()
    }

    @Test
    fun `refuses metadata that names another version than its release`() = runBlocking {
        serveReleases(release("1.0.0-alpha14", edit = { it.copy(version = "1.0.0-alpha15") }))

        val service = service()
        service.check()

        assertEquals(
            HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata("metadata for 1.0.0-alpha15 under 1.0.0-alpha14")),
            service.stateFlow.value.status,
        )
    }

    @Test
    fun `a host the launcher did not start looks nothing up`() = runBlocking {
        val service = service(hostLaunch = HostLaunch.Standalone)
        service.check()

        assertEquals(HostUpdateStatus.NotManaged, service.stateFlow.value.status)
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `restarts through the launcher after this process, passing the host's arguments on`() {
        val launch = HostLaunch.ByLauncher(
            launcherContract = 1,
            launcherExecutable = "/Applications/JetWhale Debugger.app/Contents/MacOS/JetWhale Debugger",
            hostDirectory = hostDirectory,
            setAsideVersion = null,
            arguments = listOf("--server-port", "5103"),
        )
        val pid = ProcessHandle.current().pid().toString()

        assertEquals(
            listOf("/Applications/JetWhale Debugger.app/Contents/MacOS/JetWhale Debugger", "--after", pid, "--server-port", "5103"),
            service(hostLaunch = launch).launcherCommand(retryVersion = null),
        )
        assertEquals(
            listOf("/Applications/JetWhale Debugger.app/Contents/MacOS/JetWhale Debugger", "--after", pid, "--retry", "1.0.0-alpha15", "--server-port", "5103"),
            service(hostLaunch = launch).launcherCommand(retryVersion = "1.0.0-alpha15"),
        )
        assertEquals(null, service(hostLaunch = launch.copy(launcherExecutable = null)).launcherCommand(retryVersion = null))
        assertEquals(null, service(hostLaunch = HostLaunch.Standalone).launcherCommand(retryVersion = null))
    }

    @Test
    fun `says so when the launcher to restart through cannot be started`() {
        val launch = HostLaunch.ByLauncher(
            launcherContract = 1,
            launcherExecutable = hostDirectory.resolve("moved launcher").toString(),
            hostDirectory = hostDirectory,
            setAsideVersion = null,
            arguments = emptyList(),
        )

        listOf(launch, launch.copy(launcherExecutable = null)).forEach {
            val service = service(hostLaunch = it)

            assertFalse(service.startLauncherAfterExit(retryVersion = null), "$it")
            assertTrue(service.stateFlow.value.restartFailed, "$it")
        }
    }

    /** Answers [version]'s jar with a body that sends 4 bytes and then nothing; returns the signal that the body started. */
    private fun serveStalledJar(version: String): CompletableDeferred<Unit> {
        val bodyStarted = CompletableDeferred<Unit>()
        responses[jarUrl(version)] = {
            val body = bodyWriters.writer {
                channel.writeFully(jarBytes(version), 0, 4)
                channel.flush()
                bodyStarted.complete(Unit)
                awaitCancellation()
            }.channel
            respond(body, HttpStatusCode.OK)
        }
        return bodyStarted
    }

    private fun assertStagingEmpty() {
        assertFalse(versions.staging.exists() && versions.staging.listDirectoryEntries().isNotEmpty(), "staging/ still holds a download")
    }

    private suspend fun checkAndDownload(service: DefaultHostUpdateService): HostUpdateStatus {
        service.check()
        assertIs<HostUpdateStatus.Available>(service.stateFlow.value.status)
        service.download()
        return withTimeout(10.seconds) {
            service.stateFlow.first {
                it.status !is HostUpdateStatus.Available && it.status !is HostUpdateStatus.Downloading && it.status !is HostUpdateStatus.Verifying
            }.status
        }
    }

    private fun service(
        hostLaunch: HostLaunch = HostLaunch.ByLauncher(
            launcherContract = 1,
            launcherExecutable = "/Applications/JetWhale Debugger.app/Contents/MacOS/JetWhale Debugger",
            hostDirectory = hostDirectory,
            setAsideVersion = null,
            arguments = emptyList(),
        ),
    ) = DefaultHostUpdateService(
        engine = MockEngine { request ->
            val url = request.url.toString()
            requests += url
            val respond = responses[url] ?: return@MockEngine respondError(HttpStatusCode.NotFound)
            respond(request)
        },
        hostLaunch = hostLaunch,
        hostVersionInfo = HostVersionInfo("1.0.0-alpha13"),
        hostRuntime = HostRuntime(javaFeatureVersion = 21, modules = setOf("java.base", "java.desktop"), platformKey = PLATFORM),
        releaseSource = HostReleaseSource(RELEASES_URL),
        metadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases),
        versions = HostVersionsRepository(hostLaunch),
    )

    private fun serveReleases(vararg releases: TestRelease) {
        responses[RELEASES_URL] = {
            respond(
                releases.joinToString(prefix = "[", postfix = "]", transform = TestRelease::json),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        releases.filter { it.metadata != null }.forEach { release ->
            responses[metadataUrl(release.tag)] = { respond(checkNotNull(release.metadata).encode()) }
            responses.getOrPut(jarUrl(release.tag)) { { respond(jarBytes(release.tag)) } }
        }
    }

    private fun release(
        tag: String,
        draft: Boolean = false,
        withMetadata: Boolean = true,
        edit: (HostReleaseMetadata) -> HostReleaseMetadata = { it },
    ): TestRelease {
        val assets = if (withMetadata) """[{"name":"jetwhale-host-$tag.json","browser_download_url":"${metadataUrl(tag)}"}]""" else "[]"
        return TestRelease(
            tag = tag,
            json = """{"tag_name":"$tag","draft":$draft,"prerelease":true,"assets":$assets}""",
            metadata = if (withMetadata) edit(metadata(tag)) else null,
        )
    }

    private fun install(version: String) {
        val directory = hostDirectory.resolve(version)
        Files.createDirectories(directory)
        Files.write(directory.resolve(hostJarName(version, PLATFORM)), jarBytes(version))
        Files.writeString(directory.resolve("release.json"), metadata(version).encode())
    }

    private fun metadata(version: String) = HostReleaseMetadata(
        format = 1,
        version = version,
        mainClass = "com.kitakkun.jetwhale.host.MainKt",
        launcherContract = 1,
        runtime = HostRuntimeRequirements(javaFeatureVersion = 21, modules = listOf("java.base")),
        jvmArgs = emptyList(),
        platforms = mapOf(
            PLATFORM to HostPlatformRelease(
                url = jarUrl(version),
                size = jarBytes(version).size.toLong(),
                sha256 = MessageDigest.getInstance("SHA-256").digest(jarBytes(version)).joinToString("") { "%02x".format(it) },
                jvmArgs = emptyList(),
            ),
        ),
    )

    private class TestRelease(val tag: String, val json: String, val metadata: HostReleaseMetadata?)

    private companion object {
        const val PLATFORM = "macos-arm64"
        const val RELEASES_URL = "https://api.github.com/repos/kitakkun/JetWhale/releases?per_page=30"
        const val ASSET_HOST_URL = "https://objects.example.com/release-asset"

        fun metadataUrl(tag: String) = "https://github.com/kitakkun/JetWhale/releases/download/$tag/jetwhale-host-$tag.json"

        fun jarUrl(tag: String) = "https://github.com/kitakkun/JetWhale/releases/download/$tag/${hostJarName(tag, PLATFORM)}"

        fun jarBytes(version: String) = "host jar of $version".toByteArray()
    }
}
