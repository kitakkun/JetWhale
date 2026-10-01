package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.network"

internal fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

internal fun syncErrorJson(failure: JetWhaleMessagingException): String = errorJson("failed to apply on the debuggee: ${failure.message}")
