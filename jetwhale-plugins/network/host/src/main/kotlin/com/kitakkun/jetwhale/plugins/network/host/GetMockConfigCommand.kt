package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetMockConfigCommand(
    private val mockingEnabled: () -> Boolean,
    private val mockRules: () -> List<MockRule>,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getMockConfig"
    override val description = "Returns the current mock configuration: the global enabled flag and all mock rules."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject {
        put("enabled", mockingEnabled())
        put("rules", Json.encodeToJsonElement(mockRules()))
    }.toString()
}
