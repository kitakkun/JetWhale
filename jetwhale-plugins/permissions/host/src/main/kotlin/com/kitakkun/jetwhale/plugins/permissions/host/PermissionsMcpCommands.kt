package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.plugins.permissions.protocol.PERMISSIONS_PLUGIN_ID
import kotlinx.serialization.json.Json

internal const val TOOL_PREFIX = PERMISSIONS_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }
