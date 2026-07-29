package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

// Shared by the network plugin's MCP command classes (one class per file in this package).

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

/**
 * The rules live on the debuggee, so a command that could not hand its change over there has not
 * applied it — the caller is told so rather than reading a success payload.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun syncErrorResult(failure: JetWhaleMessagingException): JetWhaleMcpResult = JetWhaleMcpResult.error("failed to apply on the debuggee: ${failure.message}")
