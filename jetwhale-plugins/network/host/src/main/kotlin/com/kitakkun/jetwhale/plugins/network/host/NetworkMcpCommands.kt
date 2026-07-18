package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Shared by the network plugin's MCP command classes (one class per file in this package).

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

internal fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

internal fun syncErrorJson(failure: JetWhaleMessagingException): String = errorJson("failed to apply on the debuggee: ${failure.message}")
