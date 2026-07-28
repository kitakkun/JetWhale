package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetMockingEnabledCommand(
    private val syncMockingEnabled: suspend (Boolean) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setMockingEnabled"
    override val description = "Enables or disables HTTP response mocking globally on the debuggee."

    private val enabled by boolean("true to enable mocking, false to disable.")

    private val mockingEnabled = serializableOutput<MockingEnabledResult>()

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val enabled = arguments[this.enabled]
        return when (val failure = syncMockingEnabled(enabled)) {
            null -> mockingEnabled.result(MockingEnabledResult(enabled = enabled))
            else -> syncErrorResult(failure)
        }
    }
}
