package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelResult
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.json.JsonObject

/** An app whose actions are [actions], answering every run with [result]. */
internal class FakeActionsClient(
    var actions: List<ActionDescriptor>,
    private val options: Map<Pair<String, String>, List<String>>,
    private val result: ActionResult,
) : ActionsClient {
    val runs = mutableListOf<Pair<String, JsonObject>>()

    override suspend fun listActions(): ActionCatalog = ActionCatalog(actions)

    override suspend fun options(actionId: String, parameter: String): ActionOptions = ActionOptions(values = options[actionId to parameter].orEmpty(), error = null)

    override suspend fun run(runId: String, actionId: String, arguments: JsonObject): ActionResult {
        runs += actionId to arguments
        return result
    }

    override suspend fun cancel(runId: String): CancelResult = CancelResult(cancelled = false)
}

internal fun action(id: String, destructive: Boolean, parameters: List<ActionParameter>): ActionDescriptor = ActionDescriptor(
    id = id,
    title = id.substringAfterLast(" / "),
    group = id.substringBeforeLast(" / ", missingDelimiterValue = "").ifEmpty { null },
    description = null,
    destructive = destructive,
    scoped = false,
    parameters = parameters,
)

internal fun parameter(name: String, type: ParameterType, optional: Boolean, nullable: Boolean): ActionParameter = ActionParameter(name = name, type = type, optional = optional, nullable = nullable, description = null, enumValues = listOf("FREE", "PRO").takeIf { type == ParameterType.ENUM }.orEmpty(), hasOptions = false)

internal val succeeded: ActionResult = ActionResult(ActionOutcome.SUCCESS, text = "done", json = null, error = null, stackTrace = null, durationMillis = 5)
