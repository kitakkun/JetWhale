package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkItem
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkOperationResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class BackgroundWorkStatus(val message: String, val isError: Boolean)

/** What the user does to the app's background work from the UI. */
internal interface BackgroundWorkActions {
    fun refresh()

    fun select(key: WorkKey)

    fun cancel(source: String, target: CancelTarget)

    fun runNow(key: WorkKey)

    fun changeFilter(filter: WorkFilter)
}

/**
 * What the Background Work UI shows: the latest snapshot from the app, the history of states the
 * host has seen each piece of work in, the filter and the selection. Every action goes through
 * [client] on [scope], and a failure lands in [status] rather than being thrown.
 */
@Stable
internal class BackgroundWorkBrowser(
    private val client: BackgroundWorkClient,
    private val scope: CoroutineScope,
) : BackgroundWorkActions {
    var snapshot: BackgroundWorkSnapshot? by mutableStateOf(null)
        private set

    var history: Map<WorkKey, List<StateTransition>> by mutableStateOf(emptyMap())
        private set

    var filter: WorkFilter by mutableStateOf(WorkFilter(states = emptySet(), query = ""))
        private set

    var selectedKey: WorkKey? by mutableStateOf(null)
        private set

    var status: BackgroundWorkStatus? by mutableStateOf(null)
        private set

    val visibleItems: List<BackgroundWorkItem>
        get() = snapshot?.items.orEmpty().filter(filter::matches)

    val selectedItem: BackgroundWorkItem?
        get() = selectedKey?.let { key -> snapshot?.items?.firstOrNull { it.key == key } }

    /** Takes a snapshot the app sent or answered with, and records any state changes it shows as of [nowEpochMillis]. */
    fun updateSnapshot(next: BackgroundWorkSnapshot, nowEpochMillis: Long) {
        snapshot = next
        history = recordTransitions(history, next.items, nowEpochMillis)
    }

    suspend fun load() {
        updateSnapshot(client.snapshot(), System.currentTimeMillis())
    }

    override fun refresh() = launchReporting {
        load()
        status = BackgroundWorkStatus(message = "Reloaded from the app.", isError = false)
    }

    override fun select(key: WorkKey) {
        selectedKey = key
    }

    override fun cancel(source: String, target: CancelTarget) = launchReporting {
        updateStatus(client.cancel(source, target))
        load()
    }

    override fun runNow(key: WorkKey) = launchReporting {
        updateStatus(client.runNow(key.source, key.id))
        load()
    }

    override fun changeFilter(filter: WorkFilter) {
        this.filter = filter
    }

    private fun updateStatus(result: WorkOperationResult) {
        status = when (val error = result.error) {
            null -> BackgroundWorkStatus(message = result.message ?: "Done.", isError = false)
            else -> BackgroundWorkStatus(message = error, isError = true)
        }
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: JetWhaleMessagingException) {
                status = BackgroundWorkStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            } catch (e: Exception) {
                status = BackgroundWorkStatus(message = "Unexpected error: $e", isError = true)
            }
        }
    }
}
