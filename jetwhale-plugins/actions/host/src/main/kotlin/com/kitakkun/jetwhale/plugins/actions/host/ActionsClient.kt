package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelResult
import kotlinx.serialization.json.JsonObject

/**
 * The app's actions as the UI and the MCP commands reach them; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface ActionsClient {
    suspend fun listActions(): ActionCatalog

    suspend fun options(actionId: String, parameter: String): ActionOptions

    suspend fun run(runId: String, actionId: String, arguments: JsonObject): ActionResult

    suspend fun cancel(runId: String): CancelResult
}
