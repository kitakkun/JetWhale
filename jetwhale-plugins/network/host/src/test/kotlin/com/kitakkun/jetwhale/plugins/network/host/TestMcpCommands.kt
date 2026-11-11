package com.kitakkun.jetwhale.plugins.network.host

/** The inspector's commands for tests that draw the inspector, where only the commands' names matter. */
internal val testMcpCommands = NetworkMcpCommands(
    transactions = { emptyList() },
    mcpRedactionRules = { emptyList() },
    removeAllTransactions = { 0 },
    mockingEnabled = { false },
    mockRules = { emptyList() },
    syncMockingEnabled = { null },
    syncMockRules = { null },
)
