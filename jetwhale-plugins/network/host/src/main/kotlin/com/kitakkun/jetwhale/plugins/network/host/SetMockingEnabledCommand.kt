package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetMockingEnabledCommand(
    private val syncMockingEnabled: suspend (Boolean) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setMockingEnabled"
    override val description = "Enables or disables HTTP response mocking globally on the debuggee."
    override val parameters = mapOf(
        "enabled" to JetWhaleMcpParameterDescriptor(
            type = "boolean",
            description = "true to enable mocking, false to disable.",
        ),
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val enabled = arguments.requireBoolean("enabled")
        return when (val failure = syncMockingEnabled(enabled)) {
            null -> buildJsonObject { put("enabled", enabled) }.toString()
            else -> syncErrorJson(failure)
        }
    }
}
