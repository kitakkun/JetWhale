package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

@OptIn(ExperimentalJetWhaleApi::class)
internal fun changeNotAppliedResult(failure: JetWhaleMessagingException): JetWhaleMcpResult = JetWhaleMcpResult.error("failed to apply on the debuggee: ${failure.message}")

internal const val MCP_REDACTION_RULES_UNREAD_ERROR =
    "transactions are withheld from MCP until the app's MCP-only redaction rules have been read; try again in a few seconds"
