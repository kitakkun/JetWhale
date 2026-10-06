package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.plugins.coroutines.protocol.COROUTINES_PLUGIN_ID
import kotlinx.serialization.json.Json

internal const val TOOL_PREFIX = COROUTINES_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }
