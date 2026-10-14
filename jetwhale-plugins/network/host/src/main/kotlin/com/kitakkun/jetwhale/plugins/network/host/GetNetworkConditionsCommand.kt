package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetNetworkConditionsCommand(
    private val conditionRules: () -> List<NetworkConditionRule>,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getNetworkConditions"
    override val description = "Returns the simulated network condition rules currently applied to the app, in the order they are matched."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject {
        put("rules", Json.encodeToJsonElement(conditionRules()))
    }.toString()
}
