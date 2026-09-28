package com.kitakkun.jetwhale.plugins.actions.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One action the app offers.
 *
 * @property id Stable for as long as the action stays registered; runs name the action by it.
 * @property group The group the action was declared in, or null for a top-level action.
 * @property destructive The action changes or discards something that cannot be restored; callers
 *   confirm before running it.
 * @property scoped The action belongs to part of the UI and exists only while that part is shown.
 * @property parameters The fields of the action's argument type, in declaration order; empty when
 *   the action takes no arguments.
 */
@Serializable
data class ActionDescriptor(
    val id: String,
    val title: String,
    val group: String?,
    val description: String?,
    val destructive: Boolean,
    val scoped: Boolean,
    val parameters: List<ActionParameter>,
)

/**
 * One field of an action's argument type, derived from its serializer.
 *
 * @property optional The field has a default value and may be left out.
 * @property enumValues The accepted values when [type] is [ParameterType.ENUM].
 * @property hasOptions The app supplies suggested values for the field at run time; ask for them
 *   with `GetActionOptions`.
 */
@Serializable
data class ActionParameter(
    val name: String,
    val type: ParameterType,
    val optional: Boolean,
    val nullable: Boolean,
    val description: String?,
    val enumValues: List<String>,
    val hasOptions: Boolean,
)

/** How a parameter is entered. Anything that is not a scalar or an enum is entered as JSON. */
@Serializable
enum class ParameterType { STRING, INTEGER, NUMBER, BOOLEAN, ENUM, JSON }

/**
 * The outcome of one run.
 *
 * @property text What the action returned, as text; null when it returned nothing.
 * @property json What the action returned when it returned JSON.
 * @property error Why the run did not succeed; null exactly when [outcome] is [ActionOutcome.SUCCESS].
 * @property stackTrace The failure's stack trace, when the action threw.
 */
@Serializable
data class ActionResult(
    val outcome: ActionOutcome,
    val text: String?,
    val json: JsonElement?,
    val error: String?,
    val stackTrace: String?,
    val durationMillis: Long,
)

@Serializable
enum class ActionOutcome { SUCCESS, FAILURE, TIMEOUT, CANCELLED }
