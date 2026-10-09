package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.soil.protocol.SOIL_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionPolicy
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TOOL_PREFIX = SOIL_PLUGIN_ID

private const val HANDLE_ARGUMENT_DESCRIPTION = "The entry's handle, as listEntries reports it."

private val McpJson: Json = Json { encodeDefaults = true }

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListSoilEntriesCommand(
    private val browser: SoilCacheBrowser,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listEntries"
    override val description =
        "Lists what the app's Soil cache holds: queries, infinite queries, mutations and subscriptions, each with its handle, id, state (timestamps in epoch seconds, as Soil records them), whether it is active or only cached, whether a screen observes it, and its options. `agentEpochSeconds` is the app's clock, to judge timestamps against. Mutations the app has dropped stay listed with isGone. Read an entry's value with getEntry."

    private val kind by enumOrNull("Only entries of this kind.", SoilEntryKind.entries)
    private val namespace by stringOrNull("Only entries whose namespace contains this, case aside.")
    private val status by enumOrNull("Only entries with this status.", SoilStatus.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val agentNow = browser.agentNowEpochSeconds()
        val requestedKind = arguments[kind]
        val requestedNamespace = arguments[namespace]
        val requestedStatus = arguments[status]
        val listed = browser.listedEntries.filter { listed ->
            (requestedKind == null || listed.entry.kind == requestedKind) &&
                (requestedNamespace == null || listed.entry.id.namespace.contains(requestedNamespace, ignoreCase = true)) &&
                (requestedStatus == null || listed.entry.state.status == requestedStatus)
        }
        return buildJsonObject {
            browser.coverage?.let { put("coverage", McpJson.encodeToJsonElement(SoilCacheCoverage.serializer(), it)) }
            put("agentEpochSeconds", agentNow)
            putJsonArray("entries") { listed.forEach { add(it.toMcpJson(agentNow)) } }
            coverageNoteOf(browser.coverage, isEmpty = browser.listedEntries.isEmpty())?.let { put("note", it) }
        }.toString()
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetSoilEntryCommand(
    private val browser: SoilCacheBrowser,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getEntry"
    override val description =
        "Reads one entry of the app's Soil cache with its value: the last reply, as JSON when a serializer covers it, otherwise as toString() text, with `encoding` saying which. Also says which of invalidateEntry, resumeEntry and removeInactiveEntry apply to it, and why not when one does not."

    private val handle by string(HANDLE_ARGUMENT_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val listed = browser.listedEntryOf(arguments[handle])
        val value = if (listed.isGone) null else browser.readValue(listed.entry.handle)
        return buildJsonObject {
            put("entry", listed.toMcpJson(browser.agentNowEpochSeconds()))
            when (value) {
                null -> put("value", McpJson.encodeToJsonElement(SoilEntryValue.serializer(), SoilEntryValue.EntryGone))
                is SoilValueLoad.Loaded -> put("value", McpJson.encodeToJsonElement(SoilEntryValue.serializer(), value.value))
                is SoilValueLoad.Failed -> put("valueError", value.message)
                is SoilValueLoad.Loading -> Unit
            }
            putJsonObject("actions") {
                SoilEntryAction.entries.forEach { action ->
                    val refusal = if (listed.isGone) "Soil no longer holds this mutation." else SoilEntryActionPolicy.refusalOf(listed.entry, action)
                    putJsonObject(action.toolName) {
                        put("applies", refusal == null)
                        refusal?.let { put("reason", it) }
                    }
                }
            }
        }.toString()
    }
}

/** One tool per [SoilEntryAction]; the agent refuses an action that does not apply, saying why. */
@OptIn(ExperimentalJetWhaleApi::class)
internal class SoilEntryActionCommand(
    private val browser: SoilCacheBrowser,
    private val action: SoilEntryAction,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.${action.toolName}"
    override val description = when (action) {
        SoilEntryAction.INVALIDATE -> "Invalidates a query or infinite query in the app's Soil cache, active or inactive: Soil marks it invalidated and an observed one refetches."
        SoilEntryAction.RESUME -> "Resumes a query or subscription a screen observes: a query refetches if its data needs it, a subscription restarts."
        SoilEntryAction.REMOVE_INACTIVE -> "Removes an inactive query or subscription from the app's Soil cache. Active entries are never removed."
    }

    private val handle by string(HANDLE_ARGUMENT_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val listed = browser.listedEntryOf(arguments[handle])
        val result = browser.runAction(listed.entry.handle, action)
        return buildJsonObject {
            put("applied", result.error == null)
            result.error?.let { put("error", it) }
        }.toString()
    }
}

private val SoilEntryAction.toolName: String
    get() = when (this) {
        SoilEntryAction.INVALIDATE -> "invalidateEntry"
        SoilEntryAction.RESUME -> "resumeEntry"
        SoilEntryAction.REMOVE_INACTIVE -> "removeInactiveEntry"
    }

/** An entry as a tool result: as the agent reported it, plus what the host judges about it. */
private fun ListedSoilEntry.toMcpJson(agentNowEpochSeconds: Long): JsonObject = buildJsonObject {
    McpJson.encodeToJsonElement(SoilEntry.serializer(), entry).jsonObject.forEach { (key, value) -> put(key, value) }
    put("isStale", entry.state.isStaleAt(agentNowEpochSeconds))
    put("isGone", isGone)
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun SoilCacheBrowser.listedEntryOf(handle: String): ListedSoilEntry = listedEntries.firstOrNull { it.entry.handle == handle }
    ?: throw JetWhaleMcpArgumentException("no entry has the handle '$handle'; list them with $TOOL_PREFIX.listEntries")

/** What a caller should know when the list is empty or partial for a reason other than the app. */
private fun coverageNoteOf(coverage: SoilCacheCoverage?, isEmpty: Boolean): String? = when {
    coverage == null -> "The app's agent has not answered yet."
    !coverage.isClientReadable -> "The app handed over a ${coverage.clientClassName}, which Soil Inspector cannot read; it reads SwrCache and SwrCachePlus."
    !coverage.includesInactiveEntries -> "Only active entries are listed: the app did not hand its SwrCachePolicy to the plugin."
    isEmpty -> "The app's Soil cache is empty."
    else -> null
}
