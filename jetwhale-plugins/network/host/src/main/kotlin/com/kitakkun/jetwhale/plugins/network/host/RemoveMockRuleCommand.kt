package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class RemoveMockRuleCommand(
    private val mockRules: () -> List<MockRule>,
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.removeMockRule"
    override val description = "Removes the mock rule with the given id."
    override val parameters = mapOf(
        "id" to JetWhaleMcpParameterDescriptor(
            type = "string",
            description = "The rule id from getMockConfig or addMockRule.",
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val id = arguments.requireString("id")
        val current = mockRules()
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) throw JetWhaleMcpArgumentException("no mock rule with id: $id")
        return when (val failure = syncMockRules(remaining)) {
            null -> buildJsonObject { put("removedId", id) }.toString()
            else -> syncErrorJson(failure)
        }
    }
}
