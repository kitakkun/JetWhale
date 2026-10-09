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
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TOOL_PREFIX = SOIL_PLUGIN_ID

private const val HANDLE_ARGUMENT_DESCRIPTION = "The entry's handle, as listEntries reports it."

/** How many events listEvents returns when the caller does not say. */
private const val DEFAULT_EVENT_LIMIT = 100

/** How many of an entry's events getEntry includes. */
private const val ENTRY_EVENT_LIMIT = 20

private const val CONFLATION_NOTE =
    "Events are derived from successive readings of the cache, which Soil's state flows conflate: a fetch that starts and ends between two readings shows up as DATA_UPDATED without a duration, and a state that lasts shorter than a reading can be missed altogether. A new entry is noticed within half a second, so a fetch it was already running gets no FETCH_STARTED and no duration."

private val McpJson: Json = Json { encodeDefaults = true }

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListSoilEntriesCommand(
    private val browser: SoilCacheBrowser,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listEntries"
    override val description =
        "Lists what the app's Soil cache holds: queries, infinite queries, mutations and subscriptions, each with its handle, id, state (Soil's timestamps in epoch seconds), whether it is active or only cached, whether a screen observes it, its options, the conditions it meets and when it last had an event. " +
            "Filters and sort match the Soil Inspector's list: `search` is its search field, `conditions` its chips. `problems` calls out failures, pauses and slow fetches. Mutations the app has dropped stay listed with isGone. Read an entry's value and what its state means with getEntry."

    private val kind by enumOrNull("Only entries of this kind.", SoilEntryKind.entries)
    private val search by stringOrNull("Only entries whose namespace or one of whose tags contains this, case aside.")
    private val status by enumOrNull("Only entries with this status.", SoilStatus.entries)
    private val conditions by serializableOrNull<List<SoilEntryCondition>>(
        "Only entries meeting these conditions. Conditions of one group widen the list, groups narrow it: FAILED, FETCHING (a query fetching or a mutation running), PAUSED, STALE and INVALIDATED are one group; ACTIVE, INACTIVE and GONE another; OBSERVED and UNOBSERVED the third.",
    )
    private val sort by enumOrNull("The order: KIND (the default, grouped by kind), NAMESPACE, LAST_UPDATED (newest reply or error first) or RECENT_ACTIVITY (latest event first).", SoilEntrySort.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val agentNowEpochMillis = browser.agentNowEpochMillis()
        val listFilter = SoilEntryListFilter(agentNowEpochMillis, browser.lastActivityEpochMillisByHandle)
        val settings = SoilEntryListSettings(searchText = arguments[search].orEmpty(), conditions = arguments[conditions].orEmpty().toSet(), sort = arguments[sort] ?: SoilEntrySort.KIND)
        val requestedKind = arguments[kind]
        val requestedStatus = arguments[status]
        val shownEntries = listFilter.shownEntriesOf(browser.listedEntries, settings).filter { listed ->
            (requestedKind == null || listed.entry.kind == requestedKind) && (requestedStatus == null || listed.entry.state.status == requestedStatus)
        }
        return buildJsonObject {
            browser.coverage?.let { put("coverage", McpJson.encodeToJsonElement(SoilCacheCoverage.serializer(), it)) }
            put("agentEpochMillis", agentNowEpochMillis)
            putJsonArray("problems") {
                listFilter.problemsOf(browser.listedEntries).forEach { problem ->
                    add(
                        buildJsonObject {
                            put("condition", problem.condition.name)
                            put("text", problem.text)
                        },
                    )
                }
            }
            putJsonArray("entries") { shownEntries.forEach { add(it.toMcpJson(listFilter)) } }
            coverageNoteOf(browser.coverage, isCacheEmpty = browser.listedEntries.isEmpty())?.let { put("note", it) }
        }.toString()
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListSoilEventsCommand(
    private val browser: SoilCacheBrowser,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listEvents"
    override val description =
        "Lists what happened to the app's Soil cache, oldest first, as the Soil Inspector's timeline shows it: entries appearing, becoming inactive and being removed, fetches starting and ending with their duration and outcome, invalidations, mutations running and ending, and subscription values. " +
            "Without `since` it returns the latest events; pass the result's `lastSequence` as `since` to read only what came after, for example before and after a tap. A mutation's effects follow it in the same burst. $CONFLATION_NOTE"

    private val since by longOrNull("Only events after this sequence number, oldest first.")
    private val handle by stringOrNull("Only the events of this entry, by its handle from listEntries.")
    private val search by stringOrNull("Only the events of entries whose namespace or one of whose tags contains this, case aside.")
    private val categories by serializableOrNull<List<SoilEventCategory>>("Only events of these categories: FETCHES, INVALIDATIONS, MUTATIONS, SUBSCRIPTIONS, LIFECYCLE (appeared, active, inactive, removed, observed, unobserved).")
    private val limit by intOrNull("At most this many events; $DEFAULT_EVENT_LIMIT when omitted.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val events = browser.events
        val sinceSequence = arguments[since]
        val requestedHandle = arguments[handle]
        val searchText = arguments[search].orEmpty()
        val requestedCategories = arguments[categories].orEmpty()
        val matchingEvents = events.filter { event ->
            (sinceSequence == null || event.sequence > sinceSequence) &&
                (requestedHandle == null || event.handle == requestedHandle) &&
                (searchText.isBlank() || event.entryId.namespace.contains(searchText, ignoreCase = true) || event.entryId.tags.any { it.contains(searchText, ignoreCase = true) }) &&
                (requestedCategories.isEmpty() || event.kind.category in requestedCategories)
        }
        val maxCount = (arguments[limit] ?: DEFAULT_EVENT_LIMIT).coerceAtLeast(1)
        val returnedEvents = if (sinceSequence == null) matchingEvents.takeLast(maxCount) else matchingEvents.take(maxCount)
        return buildJsonObject {
            putJsonArray("events") { returnedEvents.forEach { add(McpJson.encodeToJsonElement(SoilEvent.serializer(), it)) } }
            put("lastSequence", listOfNotNull(returnedEvents.lastOrNull()?.sequence, sinceSequence, events.lastOrNull()?.sequence).firstOrNull() ?: 0)
            put("hasMore", sinceSequence != null && matchingEvents.size > returnedEvents.size)
            events.firstOrNull()?.let { oldest ->
                if (sinceSequence != null && sinceSequence < oldest.sequence - 1) put("note", "Events after $sinceSequence and before ${oldest.sequence} are no longer kept.")
            }
        }.toString()
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetSoilEntryCommand(
    private val browser: SoilCacheBrowser,
    private val timeOfDayFormatter: TimeOfDayFormatter,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getEntry"
    override val description =
        "Reads one entry of the app's Soil cache: `explanation` says in words what its state means and what Soil does next (why it is stale or not refetching, when a cached entry goes away, what followed a mutation), as the Soil Inspector's detail pane does; `recentEvents` are its latest events, newest first; for a mutation, `followUps` are the invalidations, data updates, fetches and subscription restarts of other entries within 2s after its last run. " +
            "`value` is the last reply, as JSON when a serializer covers it, otherwise as toString() text, with `encoding` saying which. `actions` says which of invalidateEntry, resumeEntry and removeInactiveEntry apply, and why not when one does not."

    private val handle by string(HANDLE_ARGUMENT_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val listed = browser.listedEntryOf(arguments[handle])
        val value = if (listed.isGone) null else browser.readValue(listed.entry.handle)
        val agentNowEpochMillis = browser.agentNowEpochMillis()
        val explainer = SoilEntryExplainer(agentNowEpochMillis, browser.events, timeOfDayFormatter)
        return buildJsonObject {
            put("entry", listed.toMcpJson(SoilEntryListFilter(agentNowEpochMillis, browser.lastActivityEpochMillisByHandle)))
            putJsonArray("explanation") { explainer.notesOn(listed).forEach { add(it.text) } }
            putJsonArray("recentEvents") { explainer.recentEventsOf(listed.entry.handle, ENTRY_EVENT_LIMIT).forEach { add(McpJson.encodeToJsonElement(SoilEvent.serializer(), it)) } }
            explainer.followUpsOf(listed.entry.handle)?.let { followUps ->
                putJsonObject("followUps") {
                    put("runEndSequence", followUps.runEnd.sequence)
                    putJsonArray("events") { followUps.events.forEach { add(McpJson.encodeToJsonElement(SoilEvent.serializer(), it)) } }
                }
            }
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
private fun ListedSoilEntry.toMcpJson(listFilter: SoilEntryListFilter): JsonObject = buildJsonObject {
    McpJson.encodeToJsonElement(SoilEntry.serializer(), entry).jsonObject.forEach { (key, value) -> put(key, value) }
    put("isGone", isGone)
    putJsonArray("conditions") { SoilEntryCondition.entries.filter { listFilter.meets(this@toMcpJson, it) }.forEach { add(it.name) } }
    put("lastActivityEpochMillis", listFilter.lastActivityEpochMillisOf(this@toMcpJson))
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun SoilCacheBrowser.listedEntryOf(handle: String): ListedSoilEntry = listedEntries.firstOrNull { it.entry.handle == handle }
    ?: throw JetWhaleMcpArgumentException("no entry has the handle '$handle'; list them with $TOOL_PREFIX.listEntries")

/** What a caller should know when the list is empty or partial for a reason other than the app. */
private fun coverageNoteOf(coverage: SoilCacheCoverage?, isCacheEmpty: Boolean): String? = when {
    coverage == null -> "The app's agent has not answered yet."
    !coverage.isClientReadable -> "The app handed over a ${coverage.clientClassName}, which Soil Inspector cannot read; it reads SwrCache and SwrCachePlus."
    !coverage.includesInactiveEntries -> "Only active entries are listed: the app did not hand its SwrCachePolicy to the plugin."
    isCacheEmpty -> "The app's Soil cache is empty."
    else -> null
}
