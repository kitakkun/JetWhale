package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Tool names are globally unique across plugins, so they carry the pluginId by convention. */
internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.semantics"

internal fun errorJson(message: String): String = buildJsonObject { put("error", message) }.toString()

/**
 * Every tool here reaches into the running app, so a disconnected or unresponsive agent is a normal
 * outcome rather than a bug: it is reported to the caller as an error payload instead of failing
 * the MCP server.
 */
internal fun agentErrorJson(failure: JetWhaleMessagingException): String = errorJson("the app did not answer: ${failure.message}")
