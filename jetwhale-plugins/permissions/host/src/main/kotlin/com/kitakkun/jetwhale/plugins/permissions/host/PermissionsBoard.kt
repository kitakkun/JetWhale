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

/** How many changes the timeline keeps; older ones scroll off. */
private const val TIMELINE_LIMIT = 200

internal data class PermissionsStatus(val message: String, val isError: Boolean)

/** What the user does on the permissions screen. */
internal interface PermissionsActions {
    fun refresh()

    fun request(id: String)

    fun openAppSettings()
}

/**
 * The app's permissions as the UI shows them: the last report, the changes seen since, newest
 * first, and the outcome of the last action. Every call goes through [client] on [scope], and a
 * failure lands in [status] rather than being thrown.
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

    var status: PermissionsStatus? by mutableStateOf(null)
        private set

    suspend fun load() {
        report = client.report()
    }

    /**
     * Records [changes] the agent pushed and reloads the report, whose notes and requestability
     * depend on the new states.
     */
    fun onChanged(changes: List<PermissionChange>) {
        timeline = (changes.reversed() + timeline).take(TIMELINE_LIMIT)
        launchReporting(::load)
    }

    override fun refresh() = launchReporting {
        load()
        status = PermissionsStatus(message = "Reloaded from the app.", isError = false)
    }

    override fun request(id: String) = launchReporting { show(client.request(id)) }

    override fun openAppSettings() = launchReporting { show(client.openAppSettings()) }

    private fun show(result: PermissionActionResult) {
        status = when (val error = result.error) {
            null -> PermissionsStatus(message = result.message, isError = false)
            else -> PermissionsStatus(message = error, isError = true)
        }
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: JetWhaleMessagingException) {
                status = PermissionsStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            }
        }
    }
}
