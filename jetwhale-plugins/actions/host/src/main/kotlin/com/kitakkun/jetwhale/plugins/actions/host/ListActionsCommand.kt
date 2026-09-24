package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListActionsCommand(
    private val browser: ActionsBrowser,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listActions"
    override val description =
        "Lists the debug actions the app offers right now — logging in as a test user, resetting state, opening a deep link — with the JSON Schema of each action's arguments and, where the app suggests values, the current choices. Actions marked scoped belong to the screen on show and disappear when it goes, so list again after navigating. Run one with runAction."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        browser.load()
        val actions = browser.catalog?.actions.orEmpty()
        val choicesById = actions.associate { it.id to browser.loadOptions(it) }
        return buildJsonObject {
            putJsonArray("actions") {
                actions.forEach { action ->
                    val choices = choicesById.getValue(action.id)
                    addJsonObject {
                        put("id", action.id)
                        put("title", action.title)
                        action.group?.let { put("group", it) }
                        action.description?.let { put("description", it) }
                        put("destructive", action.destructive)
                        put("scoped", action.scoped)
                        put("argumentsSchema", action.argumentsSchema())
                        if (choices.isNotEmpty()) {
                            putJsonObject("suggestedValues") {
                                choices.forEach { (parameter, values) -> putJsonArray(parameter) { values.forEach(::add) } }
                            }
                        }
                    }
                }
            }
            if (actions.isEmpty()) put("note", "The app has registered no debug actions.")
        }.toString()
    }
}

/** The JSON Schema of the arguments object `runAction` takes for this action. */
private fun ActionDescriptor.argumentsSchema(): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        parameters.forEach { parameter -> put(parameter.name, parameter.schema()) }
    }
    putJsonArray("required") { parameters.filterNot(ActionParameter::optional).forEach { add(it.name) } }
}

private fun ActionParameter.schema(): JsonObject = buildJsonObject {
    val jsonType = when (type) {
        ParameterType.STRING, ParameterType.ENUM -> "string"
        ParameterType.INTEGER -> "integer"
        ParameterType.NUMBER -> "number"
        ParameterType.BOOLEAN -> "boolean"
        ParameterType.JSON -> null
    }
    when {
        jsonType != null && nullable -> putJsonArray("type") {
            add(jsonType)
            add("null")
        }

        jsonType != null -> put("type", jsonType)
    }
    if (type == ParameterType.ENUM) {
        // enum constrains on top of type, so a nullable enum has to list null among its values.
        putJsonArray("enum") {
            enumValues.forEach(::add)
            if (nullable) add(JsonNull)
        }
    }
    description?.let { put("description", it) }
}
