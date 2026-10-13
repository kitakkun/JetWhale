package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListNavKeyTypesCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listNavKeyTypes"
    override val description =
        "Lists the NavKey types this app can be navigated to, each with its fields and a ready-to-fill JSON template. Pass query to list only the types whose name contains it. Fill a template in and pass it to pushNavKey."

    private val query by stringOrNull(
        "List only the key types whose serial name contains this text, ignoring case: a simple name such as Detail, or part of a qualified one. Lists every type if omitted or blank.",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val keyTypes = controller.keyTypes()
        val keyTypeQuery = NavKeyTypeQuery(arguments[query].orEmpty())
        val matchingKeyTypes = keyTypes.filter(keyTypeQuery::matches)
        return buildJsonObject {
            putJsonArray("keyTypes") { matchingKeyTypes.forEach { add(it.toMcpJson()) } }
            when {
                keyTypes.isEmpty() -> put(
                    "note",
                    "The app exposed no constructible key types. Keys can still be pushed by copying the `key` object of an existing entry from getBackStack.",
                )

                matchingKeyTypes.isEmpty() -> put(
                    "note",
                    "No key type matches \"${keyTypeQuery.text}\". The app has ${keyTypes.size}; call listNavKeyTypes without query to list them all.",
                )
            }
        }.toString()
    }
}

private fun NavKeyTypeDescriptor.toMcpJson(): JsonObject = buildJsonObject {
    put("serialName", serialName)
    put("template", template)
    putJsonArray("fields") {
        fields.forEach { field ->
            addJsonObject {
                put("name", field.name)
                put("type", field.type)
                put("optional", field.optional)
                put("nullable", field.nullable)
            }
        }
    }
}
