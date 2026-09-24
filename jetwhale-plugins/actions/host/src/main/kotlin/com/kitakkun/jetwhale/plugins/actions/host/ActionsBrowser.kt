package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Enough to scroll back through a session's work without the list growing without bound. */
private const val HISTORY_LIMIT = 100

internal data class ActionsStatus(val message: String, val isError: Boolean)

/** Who started a run; the history shows it, so an AI agent's runs stand out from the user's. */
internal enum class RunOrigin { USER, AI_AGENT }

/**
 * One run in the history.
 *
 * @property result null while the run is still going.
 */
internal data class RunRecord(
    val runId: String,
    val actionId: String,
    val title: String,
    val arguments: JsonObject,
    val origin: RunOrigin,
    val result: ActionResult?,
)

/** What the UI does with the actions. */
internal interface ActionsScreenActions {
    fun refresh()

    fun select(actionId: String)

    fun run(actionId: String, arguments: JsonObject)

    fun cancel(runId: String)
}

/**
 * The app's actions as the host has loaded them, the runs made this session, and the choices the
 * app offered for parameters. Every call to the app goes through [client] on [scope]; a failure to
 * reach it lands in [status] rather than being thrown.
 */
@Stable
internal class ActionsBrowser(
    private val client: ActionsClient,
    private val scope: CoroutineScope,
) : ActionsScreenActions {
    var catalog: ActionCatalog? by mutableStateOf(null)
        private set

    var selectedId: String? by mutableStateOf(null)
        private set

    /** Newest first. */
    val history: List<RunRecord> get() = runs

    private val runs = mutableStateListOf<RunRecord>()

    /** Choices per action id, then per parameter name. */
    val options: Map<String, Map<String, List<String>>> get() = loadedOptions

    private val loadedOptions = mutableStateMapOf<String, Map<String, List<String>>>()

    var status: ActionsStatus? by mutableStateOf(null)
        private set

    val selectedAction: ActionDescriptor?
        get() = catalog?.actions?.firstOrNull { it.id == selectedId }

    suspend fun load() {
        adopt(client.listActions())
    }

    /** Takes a catalog the app pushed or returned, keeping the selection when the action survives. */
    fun adopt(latest: ActionCatalog) {
        catalog = latest
        if (latest.actions.none { it.id == selectedId }) selectedId = latest.actions.firstOrNull()?.id
    }

    override fun refresh() = launchReporting {
        load()
        status = ActionsStatus(message = "Reloaded from the app.", isError = false)
    }

    override fun select(actionId: String) {
        selectedId = actionId
        val action = catalog?.actions?.firstOrNull { it.id == actionId } ?: return
        launchReporting { loadOptions(action) }
    }

    override fun run(actionId: String, arguments: JsonObject) = launchReporting {
        val result = runNow(actionId, arguments, RunOrigin.USER)
        val title = catalog?.actions?.firstOrNull { it.id == actionId }?.title ?: actionId
        status = when (result.outcome) {
            ActionOutcome.SUCCESS -> ActionsStatus(message = "$title finished in ${result.durationMillis} ms.", isError = false)
            else -> ActionsStatus(message = "$title: ${result.error}", isError = true)
        }
    }

    override fun cancel(runId: String) = launchReporting {
        client.cancel(runId)
    }

    /** Runs the action and records the run in [history]; the MCP command waits on it directly. */
    suspend fun runNow(actionId: String, arguments: JsonObject, origin: RunOrigin): ActionResult {
        val runId = UUID.randomUUID().toString()
        val title = catalog?.actions?.firstOrNull { it.id == actionId }?.title ?: actionId
        runs.add(0, RunRecord(runId = runId, actionId = actionId, title = title, arguments = arguments, origin = origin, result = null))
        if (runs.size > HISTORY_LIMIT) runs.removeRange(HISTORY_LIMIT, runs.size)
        val result = try {
            client.run(runId = runId, actionId = actionId, arguments = arguments)
        } catch (e: JetWhaleMessagingException) {
            ActionResult(ActionOutcome.FAILURE, text = null, json = null, error = "failed to reach the app: ${e.message}", stackTrace = null, durationMillis = 0)
        }
        val index = runs.indexOfFirst { it.runId == runId }
        if (index >= 0) runs[index] = runs[index].copy(result = result)
        return result
    }

    /** Asks the app for the current choices of every parameter of [action] that has some. */
    suspend fun loadOptions(action: ActionDescriptor): Map<String, List<String>> {
        val choices = action.parameters.filter(ActionParameter::hasOptions).associate { parameter ->
            parameter.name to client.options(action.id, parameter.name).values
        }
        if (choices.isNotEmpty()) loadedOptions[action.id] = choices
        return choices
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: JetWhaleMessagingException) {
                status = ActionsStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            }
        }
    }
}
