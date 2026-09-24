package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.plugins.background.protocol.BACKGROUND_WORK_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.background.protocol.WorkOperationResult
import kotlinx.serialization.json.Json

// Shared by the background work plugin's MCP command classes (one class per file in this package).

// The tool names are namespaced by the pluginId, so they follow it rather than restating it.
internal const val TOOL_PREFIX = BACKGROUND_WORK_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }

internal const val SOURCE_ARGUMENT_DESCRIPTION = "Name of the source the work belongs to, as listBackgroundWork reports it (e.g. WorkManager, JobScheduler, BGTaskScheduler)."

internal fun WorkOperationResult.toMcpJson(): String = McpJson.encodeToString(WorkOperationResult.serializer(), this)
