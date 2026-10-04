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
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            ),
        )

    override suspend fun check() {
        if (hostLaunch !is HostLaunch.ByLauncher || downloadJob?.isActive == true) return
        setStatus(HostUpdateStatus.Checking)
        val status = try {
            lookUp(hostLaunch)
        } catch (e: IOException) {
            HostUpdateStatus.Failed(HostUpdateFailure.Unreachable(e.message.orEmpty()))
        }
        logger.info("Host update check: {}", status)
        stateFlow.update { HostUpdateState(status = status, setAside = versions.setAsideVersion()) }
    }

    override fun download() {
        val offer = offer ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch { downloadAndInstall(offer) }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
    }

    override fun startLauncherAfterExit(retryVersion: String?): Boolean {
        val command = launcherCommand(retryVersion) ?: return false
        logger.info("Starting the launcher to run after this host: {}", command)
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
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
        if (response.isRateLimited()) return HostUpdateStatus.Failed(HostUpdateFailure.RateLimited)
        if (!response.status.isSuccess()) return HostUpdateStatus.Failed(HostUpdateFailure.UnexpectedResponse(response.status.value))
        val releases = try {
            json.decodeFromString<List<GitHubRelease>>(response.bodyAsText())
        } catch (e: SerializationException) {
            return HostUpdateStatus.Failed(HostUpdateFailure.BadMetadata(e.message.orEmpty()))
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
            return HostUpdateStatus.Failed(HostUpdateFailure.UnexpectedResponse(metadataResponse.status.value))
        }
        val metadataBytes = metadataResponse.readRawBytes()
        val signatureBytes = release.asset(HostReleaseMetadata.assetName(release.tagName) + SIGNATURE_SUFFIX)
            ?.let { httpClient.get(it.browserDownloadUrl) }
            ?.takeIf { it.status.isSuccess() }
            ?.readRawBytes()
        val metadata = when (val read = metadataReader.read(metadataBytes, signatureBytes)) {
            is HostReleaseMetadataResult.Read -> read.metadata
            is HostReleaseMetadataResult.NewerFormat -> return HostUpdateStatus.NeedsNewInstaller(release.tagName)
            is HostReleaseMetadataResult.Untrusted -> return HostUpdateStatus.Failed(HostUpdateFailure.BadMetadata("signature"))
            is HostReleaseMetadataResult.Malformed -> return HostUpdateStatus.Failed(HostUpdateFailure.BadMetadata(read.reason))
        }
        if (metadata.version != release.tagName) {
            return HostUpdateStatus.Failed(HostUpdateFailure.BadMetadata("metadata for ${metadata.version} under ${release.tagName}"))
        }
        val capabilities = LauncherCapabilities(
            contract = launch.launcherContract,
            javaFeatureVersion = hostRuntime.javaFeatureVersion,
            modules = hostRuntime.modules,
            platformKey = platformKey,
        )
        if (metadata.refusalOn(capabilities) != null) {
            offer = null
            return HostUpdateStatus.NeedsNewInstaller(release.tagName)
        }
        val platform = metadata.platforms.getValue(platformKey)
        offer = HostReleaseOffer(metadata, metadataBytes, signatureBytes, platform, platformKey)
        return HostUpdateStatus.Available(metadata.version, platform.size)
    }

    private suspend fun downloadAndInstall(offer: HostReleaseOffer) {
        val version = offer.metadata.version
        setStatus(HostUpdateStatus.Downloading(version, downloadedBytes = 0, totalBytes = offer.platform.size))
        try {
            versions.clearForDownload(runningVersion = hostVersionInfo.version)
            stateFlow.update { it.copy(setAside = versions.setAsideVersion()) }
            val staging = versions.newStagingDirectory(version)
            Files.write(staging.resolve(InstalledHostVersion.METADATA_FILE_NAME), offer.metadataBytes)
            offer.signatureBytes?.let { Files.write(staging.resolve(InstalledHostVersion.SIGNATURE_FILE_NAME), it) }
            val jar = staging.resolve(hostJarName(version, offer.platformKey))
            val failure = downloadJar(offer, jar)
            if (failure != null) {
                versions.discardStaging()
                setStatus(HostUpdateStatus.Failed(failure))
                return
            }
            val check = offer.platform.check(jar)
            if (check != HostJarCheck.Matches) {
                logger.warn("Discarded the download of host {}: {}", version, check)
                versions.discardStaging()
                setStatus(HostUpdateStatus.Failed(HostUpdateFailure.Corrupted))
                return
            }
            currentCoroutineContext().ensureActive()
            versions.install(staging, version)
            logger.info("Installed host {} for the next start", version)
            this.offer = null
            stateFlow.update { HostUpdateState(status = HostUpdateStatus.ReadyToRestart(version), setAside = versions.setAsideVersion()) }
        } catch (e: CancellationException) {
            versions.discardStaging()
            stateFlow.update {
                HostUpdateState(status = HostUpdateStatus.Available(version, offer.platform.size), setAside = versions.setAsideVersion())
            }
            throw e
        } catch (e: IOException) {
            versions.discardStaging()
            setStatus(HostUpdateStatus.Failed(HostUpdateFailure.Unreachable(e.message.orEmpty())))
        }
    }

    /** Streams the jar into [jar], reporting progress; returns why it could not, or null. */
    private suspend fun downloadJar(offer: HostReleaseOffer, jar: Path): HostUpdateFailure? {
        val version = offer.metadata.version
        return httpClient.prepareGet(offer.platform.url).execute { response ->
            if (!response.status.isSuccess()) return@execute HostUpdateFailure.UnexpectedResponse(response.status.value)
            val body = response.bodyAsChannel()
            Files.newOutputStream(jar).use { output ->
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val read = body.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    setStatus(HostUpdateStatus.Downloading(version, downloaded, offer.platform.size))
                }
            }
            null
        }
    }

    private fun setStatus(status: HostUpdateStatus) {
        stateFlow.update { it.copy(status = status) }
    }

    private fun HttpResponse.isRateLimited(): Boolean = (status == HttpStatusCode.Forbidden || status == HttpStatusCode.TooManyRequests) &&
        headers["x-ratelimit-remaining"] == "0"

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
