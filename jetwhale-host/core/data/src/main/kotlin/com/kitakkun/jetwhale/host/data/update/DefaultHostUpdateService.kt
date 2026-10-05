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
import com.kitakkun.jetwhale.host.release.InstalledHostVersion
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
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Files
import java.nio.file.Path

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
    private val hostVersionInfo: HostVersionInfo,
    private val hostRuntime: HostRuntime,
    private val releaseSource: HostReleaseSource,
    private val metadataReader: HostReleaseMetadataReader,
    private val versions: HostVersionsRepository,
) : HostUpdateService {
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
    private var offer: HostReleaseOffer? = null

    @Volatile
    private var downloadJob: Job? = null

    override val stateFlow: StateFlow<HostUpdateState>
        field = MutableStateFlow(
            HostUpdateState(
                status = if (hostLaunch is HostLaunch.ByLauncher) HostUpdateStatus.NotChecked else HostUpdateStatus.NotManaged,
                setAside = versions.setAsideVersion(),
                restartFailed = false,
            ),
        )

    override suspend fun check() {
        if (hostLaunch !is HostLaunch.ByLauncher || downloadJob?.isActive == true) return
        setStatus(HostUpdateStatus.Checking)
        var status: HostUpdateStatus = HostUpdateStatus.NotChecked
        try {
            status = try {
                lookUp(hostLaunch)
            } catch (e: IOException) {
                logger.warn("Could not reach GitHub to check for host updates", e)
                HostUpdateStatus.CheckFailed(HostUpdateFailure.Unreachable)
            } catch (e: UnresolvedAddressException) {
                logger.warn("Could not reach GitHub to check for host updates", e)
                HostUpdateStatus.CheckFailed(HostUpdateFailure.Unreachable)
            }
            logger.info("Host update check: {}", status)
        } finally {
            stateFlow.update { it.copy(status = status, setAside = versions.setAsideVersion()) }
        }
    }

    override fun download() {
        val offer = offer ?: return
        if (downloadJob?.isActive == true) return
        setStatus(HostUpdateStatus.Downloading(offer.metadata.version, downloadedBytes = 0, totalBytes = offer.platform.size))
        // Started only once it is in downloadJob: a collector of the status the job publishes can
        // cancel it before this thread reaches the assignment.
        val job = scope.launch(start = CoroutineStart.LAZY) { downloadAndInstall(offer) }
        downloadJob = job
        job.start()
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
    }

    override fun startLauncherAfterExit(retryVersion: String?): Boolean {
        val command = launcherCommand(retryVersion)
        if (command == null) {
            logger.warn("Cannot restart: the launcher that started this host did not give its own path")
            stateFlow.update { it.copy(restartFailed = true) }
            return false
        }
        logger.info("Starting the launcher to run after this host: {}", command)
        try {
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: IOException) {
            logger.warn("Cannot restart: the launcher {} did not start", command.first(), e)
            stateFlow.update { it.copy(restartFailed = true) }
            return false
        }
        return true
    }

    /**
     * The launcher with `--after` this process, `--retry` when given, and the host's own arguments;
     * null when no launcher started this host, or it could not tell its own path.
     */
    @VisibleForTesting
    internal fun launcherCommand(retryVersion: String?): List<String>? {
        val launch = hostLaunch as? HostLaunch.ByLauncher ?: return null
        val launcherExecutable = launch.launcherExecutable ?: return null
        return buildList {
            add(launcherExecutable)
            add(LauncherContract.AFTER_ARGUMENT)
            add(ProcessHandle.current().pid().toString())
            if (retryVersion != null) {
                add(LauncherContract.RETRY_ARGUMENT)
                add(retryVersion)
            }
            addAll(launch.arguments)
        }
    }

    private suspend fun lookUp(launch: HostLaunch.ByLauncher): HostUpdateStatus {
        val running = HostVersion.parse(hostVersionInfo.version) ?: return HostUpdateStatus.NotManaged
        val platformKey = hostRuntime.platformKey ?: return HostUpdateStatus.NotManaged
        val installed = versions.installedVersions()
        val newestKnown = (installed.map(InstalledHostVersion::version) + running).max()

        val response = httpClient.get(releaseSource.releasesUrl)
        if (response.isRateLimited()) return HostUpdateStatus.CheckFailed(HostUpdateFailure.RateLimited)
        if (!response.status.isSuccess()) return HostUpdateStatus.CheckFailed(HostUpdateFailure.UnexpectedResponse(response.status.value))
        val releases = try {
            json.decodeFromString<List<GitHubRelease>>(response.bodyAsText())
        } catch (e: SerializationException) {
            return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata(e.message.orEmpty()))
        }

        val candidate = releases
            .filterNot(GitHubRelease::draft)
            .mapNotNull { release -> HostVersion.parse(release.tagName)?.let { release to it } }
            .filter { (release, version) -> version > newestKnown && release.asset(HostReleaseMetadata.assetName(release.tagName)) != null }
            .maxByOrNull { it.second }
            ?.first
        if (candidate == null) {
            offer = null
            val setAside = versions.setAsideVersion()?.version
            val waiting = installed.firstOrNull { it.version > running && it.name != setAside }
            return waiting?.let { HostUpdateStatus.ReadyToRestart(it.name) } ?: HostUpdateStatus.UpToDate
        }
        return offerOf(candidate, launch, platformKey)
    }

    private suspend fun offerOf(release: GitHubRelease, launch: HostLaunch.ByLauncher, platformKey: String): HostUpdateStatus {
        val metadataAsset = release.asset(HostReleaseMetadata.assetName(release.tagName)) ?: return HostUpdateStatus.UpToDate
        val metadataResponse = httpClient.get(metadataAsset.browserDownloadUrl)
        if (!metadataResponse.status.isSuccess()) {
            return HostUpdateStatus.CheckFailed(HostUpdateFailure.UnexpectedResponse(metadataResponse.status.value))
        }
        val metadataBytes = metadataResponse.readRawBytes()
        val signatureBytes = release.asset(HostReleaseMetadata.assetName(release.tagName) + SIGNATURE_SUFFIX)
            ?.let { httpClient.get(it.browserDownloadUrl) }
            ?.takeIf { it.status.isSuccess() }
            ?.readRawBytes()
        val metadata = when (val read = metadataReader.read(metadataBytes, signatureBytes)) {
            is HostReleaseMetadataResult.Read -> read.metadata
            is HostReleaseMetadataResult.NewerFormat -> return HostUpdateStatus.NeedsNewInstaller(release.tagName)
            is HostReleaseMetadataResult.Untrusted -> return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata("signature"))
            is HostReleaseMetadataResult.Malformed -> return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata(read.reason))
        }
        if (metadata.version != release.tagName) {
            return HostUpdateStatus.CheckFailed(HostUpdateFailure.BadMetadata("metadata for ${metadata.version} under ${release.tagName}"))
        }
        val capabilities = LauncherCapabilities(
            contract = launch.launcherContract,
            javaFeatureVersion = hostRuntime.javaFeatureVersion,
            modules = hostRuntime.modules,
            platformKey = platformKey,
        )
        val refusal = metadata.refusalOn(capabilities)
        if (refusal != null) {
            offer = null
            return if (refusal is HostReleaseRefusal.NoBuildForPlatform) {
                HostUpdateStatus.NoBuildForThisComputer(release.tagName)
            } else {
                HostUpdateStatus.NeedsNewInstaller(release.tagName)
            }
        }
        val platform = metadata.platforms.getValue(platformKey)
        offer = HostReleaseOffer(metadata, metadataBytes, signatureBytes, platform, platformKey)
        return HostUpdateStatus.Available(metadata.version, platform.size)
    }

    /**
     * Runs one download. Whatever ends it, `staging/` is cleared and the status leaves
     * [HostUpdateStatus.Downloading]; a cancel, or an error it does not know, offers the release again.
     */
    private suspend fun downloadAndInstall(offer: HostReleaseOffer) {
        val version = offer.metadata.version
        var status: HostUpdateStatus = HostUpdateStatus.Available(version, offer.platform.size)
        try {
            status = try {
                fetchVerifyAndInstall(offer)
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
                versions.discardStaging()
            } catch (e: IOException) {
                logger.warn("Could not clear the staging directory of host downloads", e)
            }
            stateFlow.update { it.copy(status = status, setAside = versions.setAsideVersion()) }
        }
    }

    private suspend fun fetchVerifyAndInstall(offer: HostReleaseOffer): HostUpdateStatus {
        val version = offer.metadata.version
        val staging = onLocalFiles {
            versions.clearForDownload(runningVersion = hostVersionInfo.version)
            versions.newStagingDirectory(version)
        }
        stateFlow.update { it.copy(setAside = versions.setAsideVersion()) }
        onLocalFiles {
            Files.write(staging.resolve(InstalledHostVersion.METADATA_FILE_NAME), offer.metadataBytes)
            offer.signatureBytes?.let { Files.write(staging.resolve(InstalledHostVersion.SIGNATURE_FILE_NAME), it) }
        }
        val jar = staging.resolve(hostJarName(version, offer.platformKey))
        downloadJar(offer, jar)?.let { return HostUpdateStatus.DownloadFailed(it) }
        setStatus(HostUpdateStatus.Verifying(version))
        val check = onLocalFiles { offer.platform.check(jar) }
        if (check != HostJarCheck.Matches) {
            logger.warn("Discarded the download of host {}: {}", version, check)
            return HostUpdateStatus.DownloadFailed(HostUpdateFailure.Corrupted)
        }
        currentCoroutineContext().ensureActive()
        onLocalFiles { versions.install(staging, version) }
        logger.info("Installed host {} for the next start", version)
        this.offer = null
        return HostUpdateStatus.ReadyToRestart(version)
    }

    /** Streams the jar into [jar], reporting progress; returns why it could not, or null. */
    private suspend fun downloadJar(offer: HostReleaseOffer, jar: Path): HostUpdateFailure? {
        val version = offer.metadata.version
        return httpClient.prepareGet(offer.platform.url).execute { response ->
            if (!response.status.isSuccess()) return@execute HostUpdateFailure.UnexpectedResponse(response.status.value)
            val body = response.bodyAsChannel()
            onLocalFiles { Files.newOutputStream(jar) }.use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val read = body.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    downloaded += read
                    if (downloaded > offer.platform.size) {
                        logger.warn("Stopped the download of host {}: it ran past the {} bytes its metadata pins", version, offer.platform.size)
                        return@execute HostUpdateFailure.Corrupted
                    }
                    onLocalFiles { output.write(buffer, 0, read) }
                    setStatus(HostUpdateStatus.Downloading(version, downloaded, offer.platform.size))
                }
                // readAvailable returns -1 for a channel a failure closed before the call, as it
                // does for one that ended.
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

    private class HostReleaseOffer(
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
        val json = Json { ignoreUnknownKeys = true }
    }
}
