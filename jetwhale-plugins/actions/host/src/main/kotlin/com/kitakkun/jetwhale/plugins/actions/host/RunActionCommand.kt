package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class RunActionCommand(
    private val browser: ActionsBrowser,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.runAction"
    override val description =
        "Runs one of the app's debug actions and returns its outcome (SUCCESS, FAILURE, TIMEOUT or CANCELLED), whatever it returned as text or JSON, and on failure the error with its stack trace. Get ids and argument schemas from listActions. A destructive action is refused unless confirmDestructive is true."

    private val id by string("The action's id, as listActions reports it.")
    private val actionArguments by jsonObjectOrNull("The action's arguments, matching its argumentsSchema. Omit for an action that takes none.", name = "arguments")
    private val confirmDestructive by booleanOrNull("Must be true to run an action listActions marks destructive; it changes or discards something that cannot be restored.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val actionId = arguments[id]
        val action = browser.catalog?.actions?.firstOrNull { it.id == actionId }
            ?: browser.load().let { browser.catalog?.actions?.firstOrNull { it.id == actionId } }
            ?: throw JetWhaleMcpArgumentException("no action has the id '$actionId'; call listActions for the current ones")
        if (action.destructive && arguments[confirmDestructive] != true) {
            throw JetWhaleMcpArgumentException("'${action.title}' is destructive; call again with confirmDestructive: true if running it is intended")
        }
        val result = browser.runNow(action.id, arguments[actionArguments] ?: JsonObject(emptyMap()), RunOrigin.AI_AGENT)
        return buildJsonObject {
            put("outcome", result.outcome.name)
            result.text?.let { put("text", it) }
            result.json?.let { put("json", it) }
            result.error?.let { put("error", it) }
            result.stackTrace?.let { put("stackTrace", it) }
            put("durationMillis", result.durationMillis)
        }.toString()
    }
}
