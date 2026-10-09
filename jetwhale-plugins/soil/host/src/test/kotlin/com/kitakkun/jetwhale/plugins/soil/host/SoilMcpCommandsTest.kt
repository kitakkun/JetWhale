package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalJetWhaleApi::class)
class SoilMcpCommandsTest {
    private val profile = queryEntry(handle = "query-1", namespace = "users/profile", staleAt = HOST_NOW - 10)
    private val cachedProfile = queryEntry(handle = "query-2", namespace = "users/profile", location = SoilEntryLocation.INACTIVE, isObserved = false)
    private val failedSettings = queryEntry(handle = "query-3", namespace = "settings", status = SoilStatus.FAILURE)
    private val rename = mutationEntry(handle = "mutation-4", namespace = "users/rename")
    private val client = FakeSoilCacheClient(snapshotOf(profile, cachedProfile, failedSettings, rename))
    private val browser = SoilCacheBrowser(client, CoroutineScope(Dispatchers.Unconfined), FixedClock).apply { runBlocking { load() } }

    @Test
    fun `listEntries narrows by namespace and status and judges staleness by the app clock`() {
        val listed = ListSoilEntriesCommand(browser).run(
            buildJsonObject {
                put("namespace", "PROFILE")
                put("status", "success")
            },
        ).getValue("entries").jsonArray.map(JsonElement::jsonObject)

        assertEquals(listOf("query-1", "query-2"), listed.map { it.getValue("handle").jsonPrimitive.content })
        assertEquals(true, listed.first().getValue("isStale").jsonPrimitive.boolean)
    }

    @Test
    fun `listEntries keeps a mutation the app dropped and marks it gone`() {
        browser.adopt(SoilEntriesChanged(upserts = emptyList(), removedHandles = listOf(rename.handle), revision = 2, agentEpochMillis = HOST_NOW * 1000))

        val listed = ListSoilEntriesCommand(browser).run(buildJsonObject { put("kind", "mutation") }).getValue("entries").jsonArray.single().jsonObject

        assertEquals(true, listed.getValue("isGone").jsonPrimitive.boolean)
    }

    @Test
    fun `getEntry reads the value and says which actions apply`() {
        val result = GetSoilEntryCommand(browser).run(buildJsonObject { put("handle", "query-2") })

        assertEquals(listOf("query-2"), client.valueRequests)
        assertEquals("value of query-2", result.getValue("value").jsonObject.getValue("json").jsonPrimitive.content)
        val actions = result.getValue("actions").jsonObject
        assertEquals(true, actions.getValue("removeInactiveEntry").jsonObject.getValue("applies").jsonPrimitive.boolean)
        assertEquals("Resume reaches active entries only, and this one is inactive.", actions.getValue("resumeEntry").jsonObject.getValue("reason").jsonPrimitive.content)
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
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
