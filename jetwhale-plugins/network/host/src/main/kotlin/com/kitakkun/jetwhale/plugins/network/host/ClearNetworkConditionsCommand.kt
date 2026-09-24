package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class ClearNetworkConditionsCommand(
    private val syncConditionRules: suspend (List<NetworkConditionRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.clearNetworkConditions"
    override val description = "Removes every simulated network condition, so the app's requests reach the network as they normally would."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = when (val failure = syncConditionRules(emptyList())) {
        null -> buildJsonObject { put("cleared", true) }.toString()
        else -> syncErrorJson(failure)
    }
}
