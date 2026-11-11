package com.kitakkun.jetwhale.plugins.network.host

/** The inspector's commands for previews and docs screenshots, which draw the controls but never run a command. */
internal val previewMcpCommands = NetworkMcpCommands(
    transactions = { emptyList() },
    mcpRedactionRules = { emptyList() },
    removeAllTransactions = { 0 },
    mockingEnabled = { false },
    mockRules = { emptyList() },
    syncMockingEnabled = { null },
    syncMockRules = { null },
)
