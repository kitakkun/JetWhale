package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.plugins.mainthread.protocol.MAIN_THREAD_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Shared by the main thread plugin's MCP command classes (one class per file in this package).

// The tool names are namespaced by the pluginId, so they follow it rather than restating it.
internal const val TOOL_PREFIX = MAIN_THREAD_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }

/**
 * A tool result: what the platform can report, the thresholds in force and how long recording has
 * run — without them a caller cannot tell "nothing blocked" from "nothing was watched" — followed
 * by the part of [report] the tool is about.
 */
internal fun reportJson(report: MainThreadReport, body: JsonObjectBuilder.() -> Unit): String = buildJsonObject {
    put("capabilities", McpJson.encodeToJsonElement(MonitorCapabilities.serializer(), report.capabilities))
    put("settings", McpJson.encodeToJsonElement(MonitorSettings.serializer(), report.settings))
    put("recordingSinceEpochMillis", report.recordingSinceEpochMillis)
    body()
}.toString()
