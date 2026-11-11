package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.EmptyCoroutineContext

/** The browser behind the commands for previews and docs screenshots, which draw the list but never run a command. */
private val previewBrowser = ActionsBrowser(client = NoActionsClient, scope = CoroutineScope(EmptyCoroutineContext))

internal val previewListActionsCommand = ListActionsCommand(previewBrowser)

internal val previewRunActionCommand = RunActionCommand(previewBrowser)

private object NoActionsClient : ActionsClient {
    override suspend fun listActions(): ActionCatalog = ActionCatalog(emptyList())

    override suspend fun options(actionId: String, parameter: String): ActionOptions = ActionOptions(values = emptyList(), error = null)

    override suspend fun run(runId: String, actionId: String, arguments: JsonObject, confirmedDestructive: Boolean): ActionResult = error("A preview runs no action")

    override suspend fun cancel(runId: String): CancelResult = CancelResult(cancelled = false)
}
