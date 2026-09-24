package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The form's starting text for each parameter: what the action last ran with, where there is one.
 * A boolean the app requires starts as "false", since a switch has no blank state.
 */
internal fun initialFormValues(parameters: List<ActionParameter>, remembered: JsonObject?): Map<String, String> = parameters.associate { parameter ->
    val previous = remembered?.get(parameter.name)
    parameter.name to when {
        previous == null || previous is JsonNull -> if (parameter.type == ParameterType.BOOLEAN && !parameter.optional) "false" else ""
        previous is JsonPrimitive -> previous.content
        else -> previous.toString()
    }
}

/** What the form's text makes of the arguments: a JSON object to send, or why it cannot be sent. */
internal sealed interface FormArguments {
    data class Valid(val arguments: JsonObject) : FormArguments

    /** [errors] maps a parameter name to what is wrong with its text. */
    data class Invalid(val errors: Map<String, String>) : FormArguments
}

/**
 * Turns the form's [values] into the arguments object. A blank field is left out when the property
 * has a default, sent as `null` when it is nullable, and otherwise reported as missing — a string
 * included, since a blank required field is far more often forgotten than meant to be empty.
 */
internal fun buildArguments(parameters: List<ActionParameter>, values: Map<String, String>): FormArguments {
    val arguments = mutableMapOf<String, JsonElement>()
    val errors = mutableMapOf<String, String>()
    parameters.forEach { parameter ->
        val text = values[parameter.name].orEmpty()
        when {
            text.isBlank() && parameter.optional -> Unit

            text.isBlank() && parameter.nullable -> arguments[parameter.name] = JsonNull

            text.isBlank() -> errors[parameter.name] = "required"

            else -> parameter.parse(text).fold(
                onSuccess = { arguments[parameter.name] = it },
                onFailure = { errors[parameter.name] = it.message.orEmpty() },
            )
        }
    }
    return if (errors.isEmpty()) FormArguments.Valid(JsonObject(arguments)) else FormArguments.Invalid(errors)
}

private fun ActionParameter.parse(text: String): Result<JsonElement> = when (type) {
    ParameterType.STRING -> Result.success(JsonPrimitive(text))

    ParameterType.INTEGER -> text.trim().toLongOrNull()?.let { Result.success(JsonPrimitive(it)) } ?: invalid("a whole number")

    ParameterType.NUMBER -> text.trim().toDoubleOrNull()?.let { Result.success(JsonPrimitive(it)) } ?: invalid("a number")

    ParameterType.BOOLEAN -> text.toBooleanStrictOrNull()?.let { Result.success(JsonPrimitive(it)) } ?: invalid("true or false")

    ParameterType.ENUM -> if (text in enumValues) Result.success(JsonPrimitive(text)) else invalid("one of ${enumValues.joinToString()}")

    ParameterType.JSON -> try {
        Result.success(Json.parseToJsonElement(text))
    } catch (e: SerializationException) {
        invalid("valid JSON (${e.message})")
    }
}

private fun invalid(expected: String): Result<JsonElement> = Result.failure(IllegalArgumentException("expected $expected"))
