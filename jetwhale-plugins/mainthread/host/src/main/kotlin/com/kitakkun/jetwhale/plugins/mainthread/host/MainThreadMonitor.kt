package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class MonitorStatus(val message: String, val isError: Boolean)

/** What the monitor's UI can ask for. */
internal interface MainThreadScreenActions {
    fun refresh()

    fun resetStats()

    fun updateSettings(settings: MonitorSettings)
}

/**
 * The latest report from the app and what the user does to it. Every call goes through [client]
 * on [scope]; a failure lands in [status] rather than being thrown.
 */
@Stable
internal class MainThreadMonitor(
    private val client: MainThreadClient,
    private val scope: CoroutineScope,
) : MainThreadScreenActions {
    var report: MainThreadReport? by mutableStateOf(null)
        private set

    var status: MonitorStatus? by mutableStateOf(null)
        private set

    /** Fetches the report; the screen calls this on a beat while it is shown. */
    suspend fun load() {
        report = client.requestReport()
        if (status?.isError == true) status = null
    }

    override fun refresh() = launchReporting(::load)

    override fun resetStats() = launchReporting {
        report = client.resetStats()
        status = MonitorStatus(message = "Cleared everything recorded so far.", isError = false)
    }

    override fun updateSettings(settings: MonitorSettings) = launchReporting {
        client.updateSettings(settings)
        load()
        status = MonitorStatus(message = "Thresholds updated.", isError = false)
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: JetWhaleMessagingException) {
                status = MonitorStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            } catch (e: Exception) {
                status = MonitorStatus(message = "Unexpected error: $e", isError = true)
            }
        }
    }
}
