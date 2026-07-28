package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetMockConfigCommand(
    private val mockingEnabled: () -> Boolean,
    private val mockRules: () -> List<MockRule>,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getMockConfig"
    override val description = "Returns the current mock configuration: the global enabled flag and all mock rules."

    private val mockConfig = serializableOutput<MockConfigResult>()

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = mockConfig.result(
        MockConfigResult(enabled = mockingEnabled(), rules = mockRules()),
    )
}
