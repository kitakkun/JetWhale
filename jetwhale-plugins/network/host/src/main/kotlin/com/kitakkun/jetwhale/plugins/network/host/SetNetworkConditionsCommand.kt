package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.plugins.network.protocol.problems
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetNetworkConditionsCommand(
    private val syncConditionRules: suspend (List<NetworkConditionRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setNetworkConditions"
    override val description =
        "Simulates a network for the app: added latency, bandwidth caps, injected connection failures, or being offline — for every request or per endpoint. " +
            "Pass `preset` for a ready-made network (${NetworkConditionPreset.entries.joinToString(transform = NetworkConditionPreset::name)}) applied to every request, or `rules` for the full set, replacing the current one; " +
            "the first enabled rule whose matcher fits a request applies. Conditions shape the network, not the server: a mocked response still travels through them. " +
            "They last until cleared or until the app disconnects from the host. Returns the applied rules."

    private val preset by enumOrNull("A ready-made network applied to every request. Ignored when rules is given.", NetworkConditionPreset.entries)
    private val rules by serializableOrNull<List<NetworkConditionRule>>("The full list of condition rules, replacing the current set. An empty list clears them.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val newRules = arguments[rules]
            ?: arguments[preset]?.let { listOf(it.toRule()) }
            ?: throw JetWhaleMcpArgumentException("pass either preset or rules")
        val problems = newRules.flatMap { rule -> rule.condition.problems().map { "rule '${rule.name.ifBlank { rule.id }}': $it" } }
        if (problems.isNotEmpty()) throw JetWhaleMcpArgumentException(problems.joinToString("; "))
        return when (val failure = syncConditionRules(newRules)) {
            null -> buildJsonObject { put("rules", Json.encodeToJsonElement(newRules)) }.toString()
            else -> syncErrorJson(failure)
        }
    }
}
