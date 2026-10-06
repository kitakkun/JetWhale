package com.kitakkun.jetwhale.host.data.update

import androidx.annotation.VisibleForTesting
import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.HostReleaseSource
import com.kitakkun.jetwhale.host.model.HostUpdateFailure
import com.kitakkun.jetwhale.host.model.HostUpdateService
import com.kitakkun.jetwhale.host.model.HostUpdateState
import com.kitakkun.jetwhale.host.model.HostUpdateStatus
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.HostReleaseRefusal
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionDirectory
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LauncherContract
import com.kitakkun.jetwhale.host.release.check
import com.kitakkun.jetwhale.host.release.hostJarName
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Looks up releases through the GitHub API, downloads the one the user asks for into
 * `host/staging/`, verifies it against its release's metadata, and installs it as a version
 * directory the launcher starts next time.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultHostUpdateService(
    engine: HttpClientEngine,
    private val hostLaunch: HostLaunch,
    hostVersionInfo: HostVersionInfo,
    private val hostRuntime: HostRuntime,
    private val releaseSource: HostReleaseSource,
    private val metadataReader: HostReleaseMetadataReader,
    private val hostVersionsRepository: HostVersionsRepository,
) : HostUpdateService {
    private val runningVersion: HostVersion? = HostVersion.parse(hostVersionInfo.version)

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> logger.error("A host update download failed", e) },
    )
    private val httpClient = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    @Volatile
    private var downloadableRelease: DownloadableRelease? = null

    @Volatile
    private var downloadJob: Job? = null

    override val stateFlow: StateFlow<HostUpdateState>
        field = MutableStateFlow(
            HostUpdateState(
                status = if (hostLaunch is HostLaunch.ByLauncher) HostUpdateStatus.NotChecked else HostUpdateStatus.NotManaged,
                setAside = hostVersionsRepository.newestSetAsideVersion(),
                restartFailed = false,
            ),
        )

    override suspend fun check() {
        if (hostLaunch !is HostLaunch.ByLauncher || downloadJob?.isActive == true) return
        setStatus(HostUpdateStatus.Checking)
        var status: HostUpdateStatus = HostUpdateStatus.NotChecked
        try {
            status = try {
                findNewerRelease(hostLaunch)
            } catch (e: IOException) {
                logger.warn("Could not reach GitHub to check for host updates", e)
                HostUpdateStatus.CheckFailed(HostUpdateFailure.Unreachable)
            } catch (e: UnresolvedAddressException) {
                // Ktor's CIO engine throws this for a host name it cannot resolve; it is not an
                // IOException.
                logger.warn("Could not reach GitHub to check for host updates", e)
                HostUpdateStatus.CheckFailed(HostUpdateFailure.Unreachable)
            }
            logger.info("Host update check: {}", status)
            if (status is HostUpdateStatus.CheckFailed) status = versionAwaitingRestart()?.let(HostUpdateStatus::ReadyToRestart) ?: status
        } finally {
            stateFlow.update { it.copy(status = status, setAside = hostVersionsRepository.newestSetAsideVersion()) }
        }
    }

    override fun download() {
        val downloadableRelease = downloadableRelease ?: return
        if (downloadJob?.isActive == true) return
        // An undispatched launch runs this block up to its first suspension, publishing
        // Downloading, before launch returns; the job is stored here so that a cancel made on that
        // status already finds it.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            downloadJob = coroutineContext.job
            downloadAndInstall(downloadableRelease)
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
    }

    override fun startLauncherAfterExit(retryVersion: HostVersion?): Boolean {
        val command = launcherCommand(retryVersion)
        if (command == null) {
            logger.warn("Cannot restart: the launcher that started this host did not give its own path")
            stateFlow.update { it.copy(restartFailed = true) }
            return false
        }
        logger.info("Starting the launcher to run after this host: {}", command)
        val started = try {
            val process = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            // open hands the app to LaunchServices and exits, with a nonzero status when it finds
            // no app to start; a launcher started directly waits for this process to end, so only
            // open is waited for.
            command.first() != OPEN_COMMAND || (process.waitFor(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0)
        } catch (e: IOException) {
            logger.warn("Cannot restart: {} did not start", command.first(), e)
            false
        }
        if (!started) {
            logger.warn("Cannot restart: {} did not open the app", command)
            stateFlow.update { it.copy(restartFailed = true) }
        }
        return started
    }

    /**
     * The command that starts the app again once this process has ended: its launcher with `--after`
     * this process, `--retry` when given, and the host's own arguments. On macOS it opens the app
     * bundle through LaunchServices, so the new process is the app rather than a child of this one.
     * Null when no launcher started this host, or it could not tell its own path.
     */
    @VisibleForTesting
    internal fun launcherCommand(retryVersion: HostVersion?): List<String>? {
        val launch = hostLaunch as? HostLaunch.ByLauncher ?: return null
        val launcherExecutable = launch.launcherExecutable ?: return null
        val launcherArguments = buildList {
            add(LauncherContract.AFTER_ARGUMENT)
            add(ProcessHandle.current().pid().toString())
            if (retryVersion != null) {
                add(LauncherContract.RETRY_ARGUMENT)
                add(retryVersion.name)
            }
            addAll(launch.arguments)
        }
        val appBundle = launcherExecutable.substringBefore(MAC_APP_EXECUTABLE_DIRECTORY, missingDelimiterValue = "")
        if (!appBundle.endsWith(".app")) return listOf(launcherExecutable) + launcherArguments
        return buildList {
            add(OPEN_COMMAND)
            // Without -n, open only brings the running instance forward, and that instance is this
            // process.
            add("-n")
            launch.javaToolOptions?.let {
                add("--env")
                add("JAVA_TOOL_OPTIONS=$it")
            }
            add(appBundle)
            add("--args")
            addAll(launcherArguments)
        }
    }

    private suspend fun findNewerRelease(launch: HostLaunch.ByLauncher): HostUpdateStatus {
        val runningVersion = runningVersion ?: return HostUpdateStatus.NotManaged
        val platformKey = hostRuntime.platformKey ?: return HostUpdateStatus.NotManaged
        val hostVersionDirectories = hostVersionsRepository.hostVersionDirectories()
        val newestKnownVersion = (hostVersionDirectories.map(HostVersionDirectory::version) + runningVersion).max()

        val response = httpClient.get(releaseSource.releasesUrl)
        if (response.isRateLimited()) return HostUpdateStatus.CheckFailed(HostUpdateFailure.RateLimited)
        if (!response.status.isSuccess()) return HostUpdateStatus.CheckFailed(HostUpdateFailure.UnexpectedResponse(response.status.value))
        val releases = try {
            json.decodeFromString<List<GitHubRelease>>(response.bodyAsText())
        } catch (e: SerializationException) {
            return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata(e.message.orEmpty()))
        }

        val newerRelease = releases
            .filterNot(GitHubRelease::draft)
            .mapNotNull { release -> HostVersion.parse(release.tagName)?.let { release to it } }
            .filter { (release, releaseVersion) -> releaseVersion > newestKnownVersion && release.asset(HostReleaseMetadata.assetName(releaseVersion)) != null }
            .maxByOrNull { it.second }
        if (newerRelease == null) {
            downloadableRelease = null
            return versionAwaitingRestart()?.let(HostUpdateStatus::ReadyToRestart) ?: HostUpdateStatus.UpToDate
        }
        val (release, releaseVersion) = newerRelease
        return checkNewerRelease(release, releaseVersion, launch, platformKey)
    }

    /**
     * The installed version newer than this host that is not set aside, which the next start runs.
     * A failed lookup still offers it, since nothing about it depends on the network.
     */
    private fun versionAwaitingRestart(): HostVersion? {
        val runningVersion = runningVersion ?: return null
        val setAsideVersion = hostVersionsRepository.newestSetAsideVersion()?.version
        return hostVersionsRepository.hostVersionDirectories().firstOrNull { it.version > runningVersion && it.version != setAsideVersion }?.version
    }

    private suspend fun checkNewerRelease(
        release: GitHubRelease,
        releaseVersion: HostVersion,
        launch: HostLaunch.ByLauncher,
        platformKey: String,
    ): HostUpdateStatus {
        val metadataAsset = release.asset(HostReleaseMetadata.assetName(releaseVersion)) ?: return HostUpdateStatus.UpToDate
        val metadataResponse = httpClient.get(metadataAsset.browserDownloadUrl)
        if (!metadataResponse.status.isSuccess()) {
            return HostUpdateStatus.CheckFailed(HostUpdateFailure.UnexpectedResponse(metadataResponse.status.value))
        }
        val metadataBytes = metadataResponse.readRawBytes()
        val signatureBytes = release.asset(HostReleaseMetadata.assetName(releaseVersion) + SIGNATURE_SUFFIX)
            ?.let { httpClient.get(it.browserDownloadUrl) }
            ?.takeIf { it.status.isSuccess() }
            ?.readRawBytes()
        val metadata = when (val read = metadataReader.read(metadataBytes, signatureBytes)) {
            is HostReleaseMetadataResult.Read -> read.metadata
            is HostReleaseMetadataResult.NewerFormat -> return HostUpdateStatus.NeedsNewInstaller(releaseVersion)
            is HostReleaseMetadataResult.Untrusted -> return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata("signature"))
            is HostReleaseMetadataResult.Malformed -> return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata(read.reason))
        }
        if (metadata.version.name != release.tagName) {
            return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata("metadata for ${metadata.version} under ${release.tagName}"))
        }
        val capabilities = LauncherCapabilities(
            contract = launch.launcherContract,
            javaFeatureVersion = hostRuntime.javaFeatureVersion,
            modules = hostRuntime.modules,
            platformKey = platformKey,
            jvmArguments = hostRuntime.jvmArguments,
        )
        val refusal = metadata.refusalOn(capabilities)
        if (refusal != null) {
            downloadableRelease = null
            return if (refusal is HostReleaseRefusal.NoBuildForPlatform) {
                HostUpdateStatus.NoBuildForThisComputer(releaseVersion)
            } else {
                HostUpdateStatus.NeedsNewInstaller(releaseVersion)
            }
        }
        val platform = metadata.platforms.getValue(platformKey)
        downloadableRelease = DownloadableRelease(metadata, metadataBytes, signatureBytes, platform, platformKey)
        return HostUpdateStatus.Available(metadata.version, platform.size)
    }

    /**
     * Runs one download. Whatever ends it, `staging/` is cleared and the status leaves
     * [HostUpdateStatus.Downloading]; a cancel, or an error it does not know, offers the release again.
     */
    private suspend fun downloadAndInstall(downloadableRelease: DownloadableRelease) {
        val version = downloadableRelease.metadata.version
        setStatus(HostUpdateStatus.Downloading(version, downloadedBytes = 0, totalBytes = downloadableRelease.platform.size))
        var status: HostUpdateStatus = HostUpdateStatus.Available(version, downloadableRelease.platform.size)
        try {
            status = try {
                fetchVerifyAndInstall(downloadableRelease)
            } catch (e: LocalFileException) {
                logger.warn("Could not save the download of host {}", version, e.cause)
                HostUpdateStatus.DownloadFailed(HostUpdateFailure.CouldNotSave)
            } catch (e: IOException) {
                logger.warn("Could not download host {}", version, e)
                HostUpdateStatus.DownloadFailed(HostUpdateFailure.Unreachable)
            } catch (e: UnresolvedAddressException) {
                logger.warn("Could not download host {}", version, e)
                HostUpdateStatus.DownloadFailed(HostUpdateFailure.Unreachable)
            }
        } finally {
            try {
                hostVersionsRepository.discardStaging()
            } catch (e: IOException) {
                logger.warn("Could not clear the staging directory of host downloads", e)
            }
            stateFlow.update { it.copy(status = status, setAside = hostVersionsRepository.newestSetAsideVersion()) }
        }
    }

    private suspend fun fetchVerifyAndInstall(downloadableRelease: DownloadableRelease): HostUpdateStatus {
        // download() starts this coroutine on the caller's thread; yield() moves it to
        // Dispatchers.IO before any blocking file work, and ends a download cancelled right after
        // download() returns.
        yield()
        val version = downloadableRelease.metadata.version
        val stagingDirectory = onLocalFiles {
            hostVersionsRepository.clearForDownload(runningVersion)
            hostVersionsRepository.newStagingDirectory(version)
        }
        stateFlow.update { it.copy(setAside = hostVersionsRepository.newestSetAsideVersion()) }
        onLocalFiles {
            Files.write(stagingDirectory.resolve(HostVersionDirectory.METADATA_FILE_NAME), downloadableRelease.metadataBytes)
            downloadableRelease.signatureBytes?.let { Files.write(stagingDirectory.resolve(HostVersionDirectory.SIGNATURE_FILE_NAME), it) }
        }
        val jar = stagingDirectory.resolve(hostJarName(version, downloadableRelease.platformKey))
        downloadJar(downloadableRelease, jar)?.let { return HostUpdateStatus.DownloadFailed(it) }
        setStatus(HostUpdateStatus.Verifying(version))
        val check = onLocalFiles { downloadableRelease.platform.check(jar) }
        if (check != HostJarCheck.Matches) {
            logger.warn("Discarded the download of host {}: {}", version, check)
            return HostUpdateStatus.DownloadFailed(HostUpdateFailure.Corrupted)
        }
        currentCoroutineContext().ensureActive()
        onLocalFiles { hostVersionsRepository.install(stagingDirectory, version) }
        logger.info("Installed host {} for the next start", version)
        this.downloadableRelease = null
        return HostUpdateStatus.ReadyToRestart(version)
    }

    /** Streams the jar into [jar], reporting progress; returns why it could not, or null. */
    private suspend fun downloadJar(downloadableRelease: DownloadableRelease, jar: Path): HostUpdateFailure? {
        val version = downloadableRelease.metadata.version
        return httpClient.prepareGet(downloadableRelease.platform.url).execute { response ->
            if (!response.status.isSuccess()) return@execute HostUpdateFailure.UnexpectedResponse(response.status.value)
            val body = response.bodyAsChannel()
            onLocalFiles { Files.newOutputStream(jar) }.use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val read = body.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    downloaded += read
                    if (downloaded > downloadableRelease.platform.size) {
                        logger.warn("Stopped the download of host {}: it ran past the {} bytes its metadata pins", version, downloadableRelease.platform.size)
                        return@execute HostUpdateFailure.Corrupted
                    }
                    onLocalFiles { output.write(buffer, 0, read) }
                    setStatus(HostUpdateStatus.Downloading(version, downloaded, downloadableRelease.platform.size))
                }
                // readAvailable returns -1 for a channel a failure closed, as it does for one that
                // ended normally.
                body.closedCause?.let { throw it }
            }
            null
        }
    }

    private fun setStatus(status: HostUpdateStatus) {
        stateFlow.update { it.copy(status = status) }
    }

    /** Runs [block] on this computer's files, so that its [IOException] is not taken for a network failure. */
    private inline fun <T> onLocalFiles(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw LocalFileException(e)
    }

    private class LocalFileException(override val cause: IOException) : Exception(cause)

    private fun HttpResponse.isRateLimited(): Boolean = (status == HttpStatusCode.Forbidden || status == HttpStatusCode.TooManyRequests) &&
        (headers["x-ratelimit-remaining"] == "0" || headers.contains(HttpHeaders.RetryAfter))

    private class DownloadableRelease(
        val metadata: HostReleaseMetadata,
        val metadataBytes: ByteArray,
        val signatureBytes: ByteArray?,
        val platform: HostPlatformRelease,
        val platformKey: String,
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(DefaultHostUpdateService::class.java)
        const val SIGNATURE_SUFFIX = ".sig"
        const val DOWNLOAD_BUFFER_SIZE = 1 shl 16
        const val OPEN_COMMAND = "/usr/bin/open"
        const val OPEN_TIMEOUT_SECONDS = 10L
        const val MAC_APP_EXECUTABLE_DIRECTORY = "/Contents/MacOS/"
        val json = Json { ignoreUnknownKeys = true }
    }
}
