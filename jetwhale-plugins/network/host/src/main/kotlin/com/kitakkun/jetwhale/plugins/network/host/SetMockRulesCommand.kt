package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetMockRulesCommand(
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setMockRules"
    override val description =
        "Replaces the entire set of mock rules with the given list, so an existing rule can be edited by " +
            "sending back an edited copy of the `rules` array from getMockConfig. Each rule is an object: " +
            "{id, name, enabled, matcher:{method, urlPattern, matchType}, response:{statusCode, headers, body, delayMs}}. " +
            "Returns the applied rules."

    private val rules by jsonArray("The full list of mock rules to apply, replacing the current set.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val newRules = try {
            json.decodeFromJsonElement(rulesSerializer, arguments[rules])
        } catch (e: SerializationException) {
            throw JetWhaleMcpArgumentException("invalid rules: ${e.message}")
        }
        return when (val failure = syncMockRules(newRules)) {
            null -> buildJsonObject { put("rules", json.encodeToJsonElement(newRules)) }.toString()
            else -> syncErrorJson(failure)
        }
    }

    private companion object {
        // Tolerate extra fields so a rule object copied from getMockConfig round-trips even if the
        // agent adds annotations of its own.
        val json = Json { ignoreUnknownKeys = true }
        val rulesSerializer = ListSerializer(MockRule.serializer())
    }
}
