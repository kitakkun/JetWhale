package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

@OptIn(ExperimentalJetWhaleApi::class)
internal class RemoveMockRuleCommand(
    private val mockRules: () -> List<MockRule>,
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.removeMockRule"
    override val description = "Removes the mock rule with the given id."

    private val id by string("The rule id from getMockConfig or addMockRule.")

    private val removed = serializableOutput<RemovedMockRuleResult>()

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val id = arguments[this.id]
        val current = mockRules()
        val remaining = current.filterNot { it.id == id }
        if (remaining.size == current.size) throw JetWhaleMcpArgumentException("no mock rule with id: $id")
        return when (val failure = syncMockRules(remaining)) {
            null -> removed.result(RemovedMockRuleResult(removedId = id))
            else -> syncErrorResult(failure)
        }
    }
}
