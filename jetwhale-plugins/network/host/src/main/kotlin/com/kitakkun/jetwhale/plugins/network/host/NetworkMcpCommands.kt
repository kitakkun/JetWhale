package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

@OptIn(ExperimentalJetWhaleApi::class)
internal fun syncErrorResult(failure: JetWhaleMessagingException): JetWhaleMcpResult = JetWhaleMcpResult.error("failed to apply on the debuggee: ${failure.message}")
