package com.kitakkun.jetwhale.plugins.actions.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The pluginId shared by the Debug Actions agent and host plugins. */
const val ACTIONS_PLUGIN_ID: String = "com.kitakkun.jetwhale.actions"

/** Asks the agent for every action the app has registered right now. */
@SerialName("actions/list_actions")
@Serializable
data object ListActions : JetWhaleRequest<ActionCatalog>

/**
 * Pushed by the agent whenever the set of actions changes — a screen that registers its own
 * actions entered or left composition, say.
 */
@SerialName("actions/actions_changed")
@Serializable
data class ActionsChanged(val catalog: ActionCatalog) : JetWhaleEvent

@SerialName("actions/catalog")
@Serializable
data class ActionCatalog(val actions: List<ActionDescriptor>)

/**
 * Asks for the current choices of [parameter] of the action [actionId], for a parameter whose
 * [ActionParameter.hasOptions] is true.
 */
@SerialName("actions/get_options")
@Serializable
data class GetActionOptions(
    val actionId: String,
    val parameter: String,
) : JetWhaleRequest<ActionOptions>

/** Reply to [GetActionOptions]. [error] is null exactly when [values] are the choices. */
@SerialName("actions/options")
@Serializable
data class ActionOptions(
    val values: List<String>,
    val error: String?,
)

/**
 * Runs the action [actionId] with [arguments], which must decode as the action's argument type.
 *
 * @property runId Chosen by the caller; [CancelActionRun] names the run by it.
 * @property confirmedDestructive The caller confirmed running a destructive action. The agent checks
 *   the action as it is registered now, so a caller whose catalog is out of date cannot run a
 *   destructive action it took for a harmless one.
 */
@SerialName("actions/run_action")
@Serializable
data class RunAction(
    val runId: String,
    val actionId: String,
    val arguments: JsonObject,
    val confirmedDestructive: Boolean,
) : JetWhaleRequest<ActionResult>

/** Cancels the run [runId] if it is still going; its [RunAction] then replies [ActionOutcome.CANCELLED]. */
@SerialName("actions/cancel_run")
@Serializable
data class CancelActionRun(val runId: String) : JetWhaleRequest<CancelResult>

/** Reply to [CancelActionRun]: whether a run of that id was still going. */
@SerialName("actions/cancel_result")
@Serializable
data class CancelResult(val cancelled: Boolean)
