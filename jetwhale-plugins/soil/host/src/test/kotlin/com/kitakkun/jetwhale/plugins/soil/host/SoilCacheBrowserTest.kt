package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
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
        assertEquals((HOST_NOW + 30) * 1000, browser.agentNowEpochMillis())
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

        assertEquals(2, client.snapshotRequestCount)
        assertEquals(listOf("settings"), browser.listedEntries.map { it.entry.id.namespace })
    }

    @Test
    fun `a snapshot to make up for a missing event that arrives after a newer event is taken again`() {
        runBlocking { browser.load() }
        client.queuedSnapshots += snapshotOf(profile, settings, revision = 4)
        client.snapshot = snapshotOf(settings, revision = 7)

        browser.adopt(changesOf(upserts = listOf(profile.copy(isObserved = false)), revision = 6))

        assertEquals(3, client.snapshotRequestCount)
        assertEquals(listOf("settings"), browser.listedEntries.map { it.entry.id.namespace })
    }

    @Test
    fun `a selected mutation that comes back after going away reads its value again`() {
        val renameMutation = mutationEntry(handle = "mutation-3", namespace = "users/rename")
        client.snapshot = snapshotOf(renameMutation, revision = 1)
        runBlocking { browser.load() }
        browser.select(renameMutation.handle)
        browser.adopt(changesOf(removedHandles = listOf(renameMutation.handle), revision = 2))

        browser.adopt(changesOf(upserts = listOf(renameMutation), revision = 3))

        assertEquals(listOf(renameMutation.handle, renameMutation.handle), client.valueRequests)
        assertEquals(SoilValueLoad.Loaded(SoilEntryValue.Json(encoding = SoilValueEncoding.CLASS_SERIALIZERS, json = JsonPrimitive("value of mutation-3"))), browser.selectedValue)
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

    @Test
    fun `events from the snapshot and the changes are kept in order without repeats`() {
        client.snapshot = snapshotOf(profile, settings, revision = 3, recentEvents = listOf(eventOf(1, profile, SoilEventKind.APPEARED), eventOf(2, settings, SoilEventKind.APPEARED)))
        runBlocking { browser.load() }

        browser.adopt(changesOf(revision = 4, events = listOf(eventOf(2, settings, SoilEventKind.APPEARED), eventOf(3, profile, SoilEventKind.INVALIDATED))))

        assertEquals(listOf(1L, 2L, 3L), browser.events.map(SoilEvent::sequence))
    }

    @Test
    fun `selecting an event selects its entry and reads its value`() {
        client.snapshot = snapshotOf(profile, settings, revision = 3, recentEvents = listOf(eventOf(1, settings, SoilEventKind.FETCH_STARTED)))
        runBlocking { browser.load() }

        browser.selectEvent(1)

        assertEquals(1L, browser.selectedEventSequence)
        assertEquals(settings.handle, browser.selectedEntry?.entry?.handle)
        assertEquals(listOf(settings.handle), client.valueRequests)
    }

    @Test
    fun `selecting another entry lets go of the event selected for the previous one`() {
        client.snapshot = snapshotOf(profile, settings, revision = 3, recentEvents = listOf(eventOf(1, settings, SoilEventKind.FETCH_STARTED)))
        runBlocking { browser.load() }
        browser.selectEvent(1)

        browser.select(profile.handle)

        assertNull(browser.selectedEventSequence)
    }

    @Test
    fun `clearing the timeline keeps later events and when each entry was last active`() {
        client.snapshot = snapshotOf(profile, revision = 3, recentEvents = listOf(eventOf(1, profile, SoilEventKind.APPEARED, atEpochMillis = 500)))
        runBlocking { browser.load() }

        browser.clearEvents()
        browser.adopt(changesOf(upserts = listOf(settings), revision = 4, events = listOf(eventOf(1, profile, SoilEventKind.APPEARED), eventOf(2, settings, SoilEventKind.APPEARED, atEpochMillis = 700))))

        assertEquals(listOf(2L), browser.events.map(SoilEvent::sequence))
        assertEquals(mapOf(profile.handle to 500L, settings.handle to 700L), browser.lastActivityEpochMillisByHandle)
    }

    @Test
    fun `a later snapshot adds only the events not seen yet`() {
        client.snapshot = snapshotOf(profile, revision = 3, recentEvents = (1L..2L).map { eventOf(it, profile, SoilEventKind.DATA_UPDATED) })
        runBlocking { browser.load() }

        browser.adopt(snapshotOf(profile, revision = 7, recentEvents = (1L..4L).map { eventOf(it, profile, SoilEventKind.DATA_UPDATED) }))

        assertEquals(listOf(1L, 2L, 3L, 4L), browser.events.map(SoilEvent::sequence))
    }

    @Test
    fun `an event the transport dropped comes back with the snapshot that makes up for it`() {
        client.snapshot = snapshotOf(profile, revision = 1, recentEvents = listOf(eventOf(1, profile, SoilEventKind.APPEARED)))
        runBlocking { browser.load() }
        client.snapshot = snapshotOf(profile, revision = 3, recentEvents = (1L..3L).map { eventOf(it, profile, SoilEventKind.DATA_UPDATED) })

        browser.adopt(changesOf(revision = 3, events = listOf(eventOf(3, profile, SoilEventKind.DATA_UPDATED))))

        assertEquals(listOf(1L, 2L, 3L), browser.events.map(SoilEvent::sequence))
    }

    @Test
    fun `a selected mutation that goes away shows no value`() {
        val renameMutation = mutationEntry(handle = "mutation-3", namespace = "users/rename")
        client.snapshot = snapshotOf(renameMutation, revision = 1)
        runBlocking { browser.load() }
        browser.select(renameMutation.handle)

        browser.adopt(changesOf(removedHandles = listOf(renameMutation.handle), revision = 2))

        assertEquals(SoilValueLoad.Loaded(SoilEntryValue.EntryGone), browser.selectedValue)
    }

    @Test
    fun `an entry that goes away no longer keeps its last activity`() {
        client.snapshot = snapshotOf(profile, settings, revision = 3, recentEvents = listOf(eventOf(1, settings, SoilEventKind.APPEARED, atEpochMillis = 500)))
        runBlocking { browser.load() }

        browser.adopt(changesOf(removedHandles = listOf(settings.handle), revision = 4, events = listOf(eventOf(2, settings, SoilEventKind.REMOVED, atEpochMillis = 600))))

        assertEquals(emptyMap(), browser.lastActivityEpochMillisByHandle)
    }

    @Test
    fun `the timeline keeps only the latest thousand events`() {
        runBlocking { browser.load() }

        browser.adopt(changesOf(revision = 4, events = (1L..1_001L).map { eventOf(it, profile, SoilEventKind.DATA_UPDATED) }))

        assertEquals(1_000, browser.events.size)
        assertEquals(2L, browser.events.first().sequence)
    }

    private fun changesOf(upserts: List<SoilEntry> = emptyList(), removedHandles: List<String> = emptyList(), revision: Long, events: List<SoilEvent> = emptyList()) = SoilEntriesChanged(
        upserts = upserts,
        removedHandles = removedHandles,
        revision = revision,
        agentEpochMillis = HOST_NOW * 1000,
        events = events,
    )
}
