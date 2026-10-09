package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoilCacheBrowserTest {
    private val profile = queryEntry(handle = "query-1", namespace = "users/profile")
    private val settings = queryEntry(handle = "query-2", namespace = "settings")
    private val client = FakeSoilCacheClient(snapshotOf(profile, settings, revision = 3))
    private val browser = SoilCacheBrowser(client, CoroutineScope(Dispatchers.Unconfined), FixedClock)

    @Test
    fun `a snapshot lists every entry and sets the app clock`() {
        client.snapshot = snapshotOf(profile, settings, revision = 3, agentEpochSeconds = HOST_NOW + 30)

        runBlocking { browser.load() }

        assertEquals(listOf("users/profile", "settings"), browser.listedEntries.map { it.entry.id.namespace })
        assertEquals(readableCoverage, browser.coverage)
        assertEquals(HOST_NOW + 30, browser.agentNowEpochSeconds())
    }

    @Test
    fun `changes replace entries in place and add new ones at the end`() {
        runBlocking { browser.load() }
        val feed = queryEntry(handle = "query-3", namespace = "posts/feed")

        browser.adopt(changesOf(upserts = listOf(feed, profile.copy(isObserved = false)), revision = 4))

        assertEquals(listOf("users/profile", "settings", "posts/feed"), browser.listedEntries.map { it.entry.id.namespace })
        assertEquals(false, browser.listedEntries.first().entry.isObserved)
    }

    @Test
    fun `changes the snapshot already covered are ignored`() {
        runBlocking { browser.load() }

        browser.adopt(changesOf(removedHandles = listOf(profile.handle), revision = 3))

        assertEquals(listOf("users/profile", "settings"), browser.listedEntries.map { it.entry.id.namespace })
    }

    @Test
    fun `a snapshot older than the changes already applied is ignored`() {
        runBlocking { browser.load() }
        browser.adopt(changesOf(removedHandles = listOf(settings.handle), revision = 5))

        browser.adopt(snapshotOf(profile, settings, revision = 4))

        assertEquals(listOf("users/profile"), browser.listedEntries.map { it.entry.id.namespace })
    }

    @Test
    fun `a mutation that goes away stays listed as gone while a query is dropped`() {
        val renameMutation = mutationEntry(handle = "mutation-3", namespace = "users/rename")
        client.snapshot = snapshotOf(profile, renameMutation, revision = 1)
        runBlocking { browser.load() }

        browser.adopt(changesOf(removedHandles = listOf(profile.handle, renameMutation.handle), revision = 2))

        assertEquals(listOf(ListedSoilEntry(entry = renameMutation, isGone = true)), browser.listedEntries)
    }

    @Test
    fun `a gone mutation that comes back is listed as live again`() {
        val renameMutation = mutationEntry(handle = "mutation-3", namespace = "users/rename")
        client.snapshot = snapshotOf(renameMutation, revision = 1)
        runBlocking { browser.load() }
        browser.adopt(changesOf(removedHandles = listOf(renameMutation.handle), revision = 2))

        browser.adopt(changesOf(upserts = listOf(renameMutation), revision = 3))

        assertEquals(listOf(ListedSoilEntry(entry = renameMutation, isGone = false)), browser.listedEntries)
    }

    @Test
    fun `only the latest fifty gone mutations are kept`() {
        val mutations = (1..60).map { mutationEntry(handle = "mutation-$it", namespace = "auto/$it") }
        client.snapshot = snapshotOf(*mutations.toTypedArray(), revision = 1)
        runBlocking { browser.load() }

        browser.adopt(changesOf(removedHandles = mutations.map(SoilEntry::handle), revision = 2))

        assertEquals((11..60).map { "mutation-$it" }, browser.listedEntries.map { it.entry.handle })
    }

    @Test
    fun `selecting an entry reads its value`() {
        runBlocking { browser.load() }

        browser.select(profile.handle)

        assertEquals(listOf(profile.handle), client.valueRequests)
        assertEquals(SoilValueLoad.Loaded(SoilEntryValue.Json(encoding = SoilValueEncoding.CLASS_SERIALIZERS, json = JsonPrimitive("value of query-1"))), browser.selectedValue)
    }

    @Test
    fun `a new reply for the selected entry reads its value again`() {
        runBlocking { browser.load() }
        browser.select(profile.handle)

        browser.adopt(changesOf(upserts = listOf(settings.copy(isObserved = false), profile.copy(isObserved = false)), revision = 4))
        browser.adopt(changesOf(upserts = listOf(profile.copy(replyRevision = 1)), revision = 5))

        assertEquals(listOf(profile.handle, profile.handle), client.valueRequests)
    }

    @Test
    fun `a snapshot with a new reply for the selected entry reads its value again`() {
        runBlocking { browser.load() }
        browser.select(profile.handle)

        browser.adopt(snapshotOf(profile.copy(replyRevision = 1), settings, revision = 4))

        assertEquals(listOf(profile.handle, profile.handle), client.valueRequests)
    }

    @Test
    fun `a revision skipped by the events takes a fresh snapshot`() {
        runBlocking { browser.load() }
        client.snapshot = snapshotOf(settings, revision = 6)

        browser.adopt(changesOf(upserts = listOf(profile.copy(isObserved = false)), revision = 5))

        assertEquals(2, client.snapshotRequests)
        assertEquals(listOf("settings"), browser.listedEntries.map { it.entry.id.namespace })
    }

    @Test
    fun `the selection is cleared when its entry goes away`() {
        runBlocking { browser.load() }
        browser.select(settings.handle)

        browser.adopt(changesOf(removedHandles = listOf(settings.handle), revision = 4))

        assertNull(browser.selectedEntry)
        assertNull(browser.selectedValue)
    }

    @Test
    fun `an action the app refuses lands in the status`() {
        runBlocking { browser.load() }
        browser.select(profile.handle)
        client.actionResult = SoilEntryActionResult(error = "The entry is active, and active entries are not removed.")

        browser.runActionOnSelected(SoilEntryAction.REMOVE_INACTIVE)

        assertEquals(listOf(profile.handle to SoilEntryAction.REMOVE_INACTIVE), client.actionRequests)
        assertEquals(SoilBrowserStatus(message = "Remove users/profile: The entry is active, and active entries are not removed.", isError = true), browser.status)
    }

    private fun changesOf(upserts: List<SoilEntry> = emptyList(), removedHandles: List<String> = emptyList(), revision: Long) = SoilEntriesChanged(
        upserts = upserts,
        removedHandles = removedHandles,
        revision = revision,
        agentEpochMillis = HOST_NOW * 1000,
    )
}
