package com.kitakkun.jetwhale.host.data.update

import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.UpdateCheckResult
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Checks the Conveyor-generated update site for a newer host release by fetching
 * `metadata.properties` and comparing its `app.version` against the running host's version.
 */
@SingleIn(AppScope::class)
@Inject
class UpdateCheckService(
    engine: HttpClientEngine,
    private val hostVersionInfo: HostVersionInfo,
) {
    private val httpClient = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    // Null when not running from a Conveyor package (dev runs, uber jar).
    private val updateController: SoftwareUpdateController? = SoftwareUpdateController.getInstance()

    suspend fun checkForUpdates(): UpdateCheckResult {
        val response = httpClient.get("$UPDATE_SITE_URL/metadata.properties")
        if (!response.status.isSuccess()) {
            throw UpdateCheckException("Update site responded with ${response.status}")
        }
        val latestVersion = parseAppVersion(response.bodyAsText())
            ?: throw UpdateCheckException("Update site metadata has no app.version entry")
        val currentVersion = numericVersionOf(hostVersionInfo.version)
        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            updateAvailable = isNewer(candidate = latestVersion, current = currentVersion),
            canInstallInApp = updateController?.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE,
            downloadPageUrl = DOWNLOAD_PAGE_URL,
        )
    }

    /**
     * Hands control to the OS updater (Sparkle dialog on macOS, updater exe on Windows).
     * The app may quit as part of this flow.
     */
    fun triggerUpdateInstall() {
        updateController?.triggerUpdateCheckUI()
    }

    companion object {
        private const val UPDATE_SITE_URL = "https://github.com/kitakkun/JetWhale/releases/latest/download"
        private const val DOWNLOAD_PAGE_URL = "https://github.com/kitakkun/JetWhale/releases/latest"

        /**
         * Extracts `app.version` from a Conveyor `metadata.properties` payload. The format is
         * plain `key=value` lines with `#` comments, so full .properties parsing is unnecessary.
         */
        fun parseAppVersion(properties: String): String? = properties.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("#") }
            .mapNotNull { line ->
                val key = line.substringBefore('=').trim()
                if (key == "app.version") line.substringAfter('=').trim() else null
            }
            .firstOrNull()

        /**
         * Maps the host's display version to the purely numeric package version Conveyor
         * publishes. Must mirror the mapping in jetwhale-host/app/build.gradle.kts
         * (e.g. 1.0.0-alpha08 -> 1.0.0.8).
         */
        fun numericVersionOf(version: String): String {
            val base = version.substringBefore("-")
            val preReleaseNumber = version.substringAfter("-", "").filter { it.isDigit() }.toIntOrNull()
            return if (preReleaseNumber != null) "$base.$preReleaseNumber" else base
        }

        /** Compares dotted numeric versions; missing segments count as zero. */
        fun isNewer(candidate: String, current: String): Boolean {
            val candidateSegments = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val currentSegments = current.split('.').map { it.toIntOrNull() ?: 0 }
            val length = maxOf(candidateSegments.size, currentSegments.size)
            for (i in 0 until length) {
                val a = candidateSegments.getOrElse(i) { 0 }
                val b = currentSegments.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}

class UpdateCheckException(message: String) : Exception(message)
