package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class SoilMcpCommandsTest {
    private val profile = queryEntry(handle = "query-1", namespace = "users/profile", staleAt = HOST_NOW - 10)
    private val cachedProfile = queryEntry(handle = "query-2", namespace = "users/profile", location = SoilEntryLocation.INACTIVE, staleAt = HOST_NOW + 60, isObserved = false)
    private val failedSettingsQuery = queryEntry(handle = "query-3", namespace = "settings", status = SoilStatus.FAILURE)
    private val renameMutation = mutationEntry(handle = "mutation-4", namespace = "users/rename")
    private val recentEvents = listOf(
        eventOf(1, renameMutation, SoilEventKind.MUTATION_SUCCEEDED, atEpochMillis = 990_000),
        eventOf(2, profile, SoilEventKind.INVALIDATED, atEpochMillis = 990_000),
        eventOf(3, profile, SoilEventKind.FETCH_SUCCEEDED, atEpochMillis = 990_300),
        eventOf(4, failedSettingsQuery, SoilEventKind.FETCH_FAILED, atEpochMillis = 995_000),
    )
    private val client = FakeSoilCacheClient(snapshotOf(profile, cachedProfile, failedSettingsQuery, renameMutation, recentEvents = recentEvents))
    private val browser = SoilCacheBrowser(client, CoroutineScope(Dispatchers.Unconfined), FixedClock).apply { runBlocking { load() } }

    @Test
    fun `listEntries narrows by search and conditions as the list does and says which conditions hold`() {
        val entries = ListSoilEntriesCommand(browser).run(
            buildJsonObject {
                put("search", "PROFILE")
                putJsonArray("conditions") { add("STALE") }
            },
        ).entryObjects()

        assertEquals(listOf("query-1"), entries.map { it.getValue("handle").jsonPrimitive.content })
        assertEquals(listOf("STALE", "ACTIVE", "OBSERVED"), entries.single().getValue("conditions").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `listEntries widens within a group of conditions and narrows across groups`() {
        val widened = ListSoilEntriesCommand(browser).run(
            buildJsonObject {
                putJsonArray("conditions") {
                    add("STALE")
                    add("FAILED")
                }
            },
        ).entryObjects()
        val narrowed = ListSoilEntriesCommand(browser).run(
            buildJsonObject {
                putJsonArray("conditions") {
                    add("STALE")
                    add("FAILED")
                    add("UNOBSERVED")
                }
            },
        ).entryObjects()

        assertEquals(listOf("query-1", "query-3"), widened.map { it.getValue("handle").jsonPrimitive.content })
        assertEquals(emptyList(), narrowed)
    }

    @Test
    fun `listEntries sorts by recent activity like the list`() {
        val entries = ListSoilEntriesCommand(browser).run(buildJsonObject { put("sort", "RECENT_ACTIVITY") }).entryObjects()

        assertEquals(listOf("query-3", "query-1", "mutation-4"), entries.take(3).map { it.getValue("handle").jsonPrimitive.content })
    }

    @Test
    fun `listEntries calls out failures as problems`() {
        val problems = ListSoilEntriesCommand(browser).run(buildJsonObject {}).getValue("problems").jsonArray.map(JsonElement::jsonObject)

        assertEquals(listOf("FAILED"), problems.map { it.getValue("condition").jsonPrimitive.content })
        assertEquals("1 failed", problems.single().getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `listEntries keeps a mutation the app dropped and marks it gone`() {
        browser.adopt(SoilEntriesChanged(upserts = emptyList(), removedHandles = listOf(renameMutation.handle), revision = 2, agentEpochMillis = HOST_NOW * 1000, events = emptyList()))

        val listed = ListSoilEntriesCommand(browser).run(buildJsonObject { put("kind", "mutation") }).entryObjects().single()

        assertEquals(true, listed.getValue("isGone").jsonPrimitive.boolean)
    }

    @Test
    fun `listEvents without a cursor returns the latest events and the sequence to read on from`() {
        val result = ListSoilEventsCommand(browser).run(buildJsonObject { put("limit", 2) })

        assertEquals(listOf(3L, 4L), result.sequences())
        assertEquals(4L, result.getValue("lastSequence").jsonPrimitive.long)
    }

    @Test
    fun `listEvents after a cursor returns what came next and whether more is left`() {
        val result = ListSoilEventsCommand(browser).run(
            buildJsonObject {
                put("since", 1)
                put("limit", 2)
            },
        )

        assertEquals(listOf(2L, 3L), result.sequences())
        assertEquals(true, result.getValue("hasMore").jsonPrimitive.boolean)
    }

    @Test
    fun `listEvents narrows by entry and category`() {
        val byHandle = ListSoilEventsCommand(browser).run(buildJsonObject { put("handle", "query-1") })
        val invalidations = ListSoilEventsCommand(browser).run(buildJsonObject { putJsonArray("categories") { add("INVALIDATIONS") } })

        assertEquals(listOf(2L, 3L), byHandle.sequences())
        assertEquals(listOf(2L), invalidations.sequences())
    }

    @Test
    fun `listEvents says when events after the cursor are no longer kept`() {
        browser.clearEvents()
        browser.adopt(SoilEntriesChanged(upserts = emptyList(), removedHandles = emptyList(), revision = 2, agentEpochMillis = HOST_NOW * 1000, events = listOf(eventOf(9, profile, SoilEventKind.DATA_UPDATED))))

        val result = ListSoilEventsCommand(browser).run(buildJsonObject { put("since", 4) })

        assertEquals(listOf(9L), result.sequences())
        assertEquals("Events after 4 and before 9 are no longer kept.", result.getValue("note").jsonPrimitive.content)
    }

    @Test
    fun `getEntry reads the value and says which actions apply`() {
        val result = getEntry("query-2")

        assertEquals(listOf("query-2"), client.valueRequests)
        assertEquals("value of query-2", result.getValue("value").jsonObject.getValue("json").jsonPrimitive.content)
        val actions = result.getValue("actions").jsonObject
        assertEquals(true, actions.getValue("removeInactiveEntry").jsonObject.getValue("applies").jsonPrimitive.boolean)
        assertEquals("Resume reaches active entries only, and this one is inactive.", actions.getValue("resumeEntry").jsonObject.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun `getEntry explains the state and lists the entry's latest events newest first`() {
        val result = getEntry("query-1")

        assertTrue(result.getValue("explanation").jsonArray.any { it.jsonPrimitive.content.startsWith("Stale") })
        assertEquals(listOf(3L, 2L), result.getValue("recentEvents").jsonArray.map { it.jsonObject.getValue("sequence").jsonPrimitive.long })
    }

    @Test
    fun `getEntry on a mutation lists what followed its last run`() {
        val followUps = getEntry("mutation-4").getValue("followUps").jsonObject

        assertEquals(1L, followUps.getValue("runEndSequence").jsonPrimitive.long)
        assertEquals(listOf(2L, 3L), followUps.getValue("events").jsonArray.map { it.jsonObject.getValue("sequence").jsonPrimitive.long })
    }

    @Test
    fun `an action tool hands the action to the app and passes its refusal on`() {
        client.actionResult = SoilEntryActionResult(error = "The entry has become active, and active entries are not removed.")

        val result = SoilEntryActionCommand(browser, SoilEntryAction.REMOVE_INACTIVE).run(buildJsonObject { put("handle", "query-2") })

        assertEquals(listOf("query-2" to SoilEntryAction.REMOVE_INACTIVE), client.actionRequests)
        assertEquals(false, result.getValue("applied").jsonPrimitive.boolean)
        assertEquals("The entry has become active, and active entries are not removed.", result.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `a handle nothing has is a caller mistake`() {
        assertFailsWith<JetWhaleMcpArgumentException> {
            SoilEntryActionCommand(browser, SoilEntryAction.INVALIDATE).run(buildJsonObject { put("handle", "query-99") })
        }
    }

    @Test
    fun `each action has a tool of its own`() {
        assertEquals(
            listOf("com.kitakkun.jetwhale.soil.invalidateEntry", "com.kitakkun.jetwhale.soil.resumeEntry", "com.kitakkun.jetwhale.soil.removeInactiveEntry"),
            SoilEntryAction.entries.map { SoilEntryActionCommand(browser, it).name },
        )
    }

    private fun getEntry(handle: String): JsonObject = GetSoilEntryCommand(browser, TimeOfDayFormatter(ZoneOffset.UTC)).run(buildJsonObject { put("handle", handle) })
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}

private fun JsonObject.entryObjects(): List<JsonObject> = getValue("entries").jsonArray.map(JsonElement::jsonObject)

private fun JsonObject.sequences(): List<Long> = getValue("events").jsonArray.map { it.jsonObject.getValue("sequence").jsonPrimitive.long }
