package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.storage.protocol.STORAGE_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal const val TOOL_PREFIX = STORAGE_PLUGIN_ID

internal val McpJson: Json = Json { encodeDefaults = true }

internal const val PATH_ARGUMENT_DESCRIPTION = "Path below the root, with '/' between segments, e.g. 'files/datastore/settings.preferences_pb'."

/** A tool's `root` and `path` arguments as the location they name. */
internal fun fileLocationOf(rootName: String, path: String?): FileLocation = FileLocation(rootName = rootName, path = path.orEmpty().split('/').filter(String::isNotEmpty))

/** The app's [reply] as a tool result: its [error] fails the call, and the rest of it is the answer. */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun <T> replyResult(reply: T, serializer: KSerializer<T>, error: String?): JetWhaleMcpResult = when (error) {
    null -> JetWhaleMcpResult.json(JsonObject(McpJson.encodeToJsonElement(serializer, reply).jsonObject - "error"))
    else -> JetWhaleMcpResult.error(error)
}

/** A change to the app's storage as a tool result: made, or failed with the app's reason. */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun StorageOperationResult.toMcpResult(): JetWhaleMcpResult = when (val failure = error) {
    null -> JetWhaleMcpResult.json(buildJsonObject { put("ok", true) })
    else -> JetWhaleMcpResult.error(failure)
}
