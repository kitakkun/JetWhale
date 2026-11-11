package com.kitakkun.jetwhale.plugins.semantics.host

/** The inspector's commands for previews and docs screenshots, which draw the panes but never run a command. */
internal val previewMcpCommands = SemanticsMcpCommands(
    capture = { error("A preview has no app") },
    lastSnapshot = { null },
    perform = { error("A preview has no app") },
    getAttributes = { error("A preview has no app") },
    setAttribute = { error("A preview has no app") },
)
