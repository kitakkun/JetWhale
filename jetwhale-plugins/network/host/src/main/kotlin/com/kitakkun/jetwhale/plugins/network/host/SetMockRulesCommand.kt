package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetMockRulesCommand(
    private val syncMockRules: suspend (List<MockRule>) -> JetWhaleMessagingException?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setMockRules"
    override val description =
        "Replaces the entire set of mock rules with the given list, so an existing rule can be edited by " +
            "sending back an edited copy of the `rules` array from getMockConfig. Returns the applied rules."

    private val rules by serializable<List<MockRule>>("The full list of mock rules to apply, replacing the current set.")

    private val appliedRules = serializableOutput<MockRulesResult>()

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val newRules = arguments[rules]
        return when (val failure = syncMockRules(newRules)) {
            null -> appliedRules.result(MockRulesResult(rules = newRules))
            else -> syncErrorResult(failure)
        }
    }
}
