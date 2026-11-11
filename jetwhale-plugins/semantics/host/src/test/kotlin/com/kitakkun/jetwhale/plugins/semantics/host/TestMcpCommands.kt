package com.kitakkun.jetwhale.plugins.semantics.host

/** The inspector's commands for tests that draw the inspector, where only the commands' names matter. */
internal val testMcpCommands = SemanticsMcpCommands(
    capture = { error("A screen test captures no tree") },
    lastSnapshot = { null },
    perform = { error("A screen test performs no action") },
    getAttributes = { error("A screen test reads no attributes") },
    setAttribute = { error("A screen test writes no attributes") },
)
