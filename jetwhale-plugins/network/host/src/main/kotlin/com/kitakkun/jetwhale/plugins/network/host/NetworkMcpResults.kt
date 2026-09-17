package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.McpDescription
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import kotlinx.serialization.Serializable

// Answer shapes of the mock-configuration tools. Each is declared as a command's output, so the
// schema an AI agent reads and the payload it receives are derived from the same type.

@Serializable
internal data class MockConfigResult(
    @McpDescription("Whether response mocking is enabled globally on the debuggee.")
    val enabled: Boolean,
    @McpDescription("Every configured mock rule, in the order they are matched.")
    val rules: List<MockRule>,
)

@Serializable
internal data class MockRulesResult(
    @McpDescription("The mock rules now in effect on the debuggee.")
    val rules: List<MockRule>,
)

@Serializable
internal data class MockingEnabledResult(
    @McpDescription("Whether response mocking is now enabled globally on the debuggee.")
    val enabled: Boolean,
)

@Serializable
internal data class RemovedMockRuleResult(
    @McpDescription("Id of the mock rule that was removed.")
    val removedId: String,
)

@Serializable
internal data class ClearedTransactionsResult(
    @McpDescription("How many captured transactions were discarded.")
    val clearedCount: Int,
)
