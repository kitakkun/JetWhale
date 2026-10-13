package com.kitakkun.jetwhale.plugins.permissions.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** How many changes the timeline keeps; older ones scroll off. */
private const val TIMELINE_LIMIT = 200

internal data class BannerMessage(val message: String, val isError: Boolean)

/** What the user does on the permissions screen. */
internal interface PermissionsActions {
    fun refresh()

    fun requestPermission(permissionId: String)

    fun openAppSettings()
}

/**
 * The app's permissions as the UI shows them: the last report, the changes seen since, newest
 * first, and the outcome of the last action. Every call goes through [client] on [scope], and a
 * failure lands in [bannerMessage] rather than being thrown.
 */
@Stable
internal class PermissionsBoard(
    private val client: PermissionsClient,
    private val scope: CoroutineScope,
) : PermissionsActions {
    var report: PermissionReport? by mutableStateOf(null)
        private set

    var timeline: List<PermissionChange> by mutableStateOf(emptyList())
        private set

    var bannerMessage: BannerMessage? by mutableStateOf(null)
        private set

    private val loadGeneration = AtomicInteger()

    /** Loads the first report; a failure lands in [bannerMessage] instead of leaving the screen waiting. */
    suspend fun loadCatchingFailures() = reportingFailures(::load)

    suspend fun load() {
        val generation = loadGeneration.incrementAndGet()
        val loaded = client.getPermissions()
        if (generation == loadGeneration.get()) report = loaded
    }

    /**
     * Records [changes] the agent pushed and reloads the report, whose notes and requestability
     * depend on the new states.
     */
    fun addChangesAndReloadReport(changes: List<PermissionChange>) {
        timeline = (changes.reversed() + timeline).take(TIMELINE_LIMIT)
        launchReporting(::load)
    }

    override fun refresh() = launchReporting {
        load()
        bannerMessage = BannerMessage(message = "Reloaded from the app.", isError = false)
    }

    override fun requestPermission(permissionId: String) = launchReporting { showActionResult(client.requestPermission(permissionId)) }

    override fun openAppSettings() = launchReporting { showActionResult(client.openAppSettings()) }

    private fun showActionResult(result: PermissionActionResult) {
        bannerMessage = when (val error = result.error) {
            null -> BannerMessage(message = result.message, isError = false)
            else -> BannerMessage(message = error, isError = true)
        }
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch { reportingFailures(block) }
    }

    private suspend fun reportingFailures(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: JetWhaleMessagingException) {
            bannerMessage = BannerMessage(message = "Failed to reach the app: ${e.message}", isError = true)
        }
    }
}
