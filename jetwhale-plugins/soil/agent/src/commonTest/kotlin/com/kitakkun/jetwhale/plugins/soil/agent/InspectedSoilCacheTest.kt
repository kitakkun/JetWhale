package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import soil.query.InfiniteQueryId
import soil.query.QueryCacheBuilder
import soil.query.QueryChunk
import soil.query.QueryId
import soil.query.SwrCache
import soil.query.SwrCachePolicy
import soil.query.SwrClient
import soil.query.buildQueryKey
import soil.query.core.Reply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InspectedSoilCacheTest {
    private val cachedProfileId = QueryId<String>("users/profile", 7)
    private val cachedFeedId = InfiniteQueryId<String, Int>("posts/feed")

    @Test
    fun `entries kept in the cache after their last user left are read as inactive`() = runTest {
        val policy = testPolicy()

        val records = InspectedSoilCache(SwrCache(policy), policy).readRecords()

        assertEquals(
            setOf(SoilEntryKey(SoilEntryKind.QUERY, cachedProfileId) to SoilEntryLocation.INACTIVE, SoilEntryKey(SoilEntryKind.INFINITE_QUERY, cachedFeedId) to SoilEntryLocation.INACTIVE),
            records.map { it.key to it.location }.toSet(),
        )
        assertTrue(records.none(SoilCacheRecord::isObserved))
    }

    @Test
    fun `an active query is read as observed with its options`() = runTest {
        val policy = testPolicy()
        val client = SwrCache(policy)
        val activeId = QueryId<String>("settings")
        client.getQuery(buildQueryKey(activeId) { "dark" })

        val record = InspectedSoilCache(client, policy).readRecords().single { it.key.id == activeId }

        assertEquals(SoilEntryLocation.ACTIVE, record.location)
        assertTrue(record.isObserved)
        assertEquals("5m", record.options["gcTime"])
        assertNotNull(record.stateFlow)
    }

    @Test
    fun `an inactive entry is removed from the cache`() = runTest {
        val policy = testPolicy()
        val cache = InspectedSoilCache(SwrCache(policy), policy)

        val refusal = cache.removeInactive(SoilEntryKey(SoilEntryKind.QUERY, cachedProfileId))

        assertNull(refusal)
        assertTrue(cache.readRecords().none { it.key.id == cachedProfileId })
    }

    @Test
    fun `an entry that became active is not removed`() = runTest {
        val policy = testPolicy()
        val client = SwrCache(policy)
        client.getQuery(buildQueryKey(cachedProfileId) { "Ada" })
        val cache = InspectedSoilCache(client, policy)

        val refusal = cache.removeInactive(SoilEntryKey(SoilEntryKind.QUERY, cachedProfileId))

        assertNotNull(refusal)
        assertEquals(SoilEntryLocation.ACTIVE_AND_CACHED, cache.readRecords().single { it.key.id == cachedProfileId }.location)
    }

    @Test
    fun `a cached reply is read back`() = runTest {
        val policy = testPolicy()

        val reply = InspectedSoilCache(SwrCache(policy), policy).readReply(SoilEntryKey(SoilEntryKind.INFINITE_QUERY, cachedFeedId))

        assertEquals(Reply.some(listOf(QueryChunk(data = "first page", param = 1))), reply)
    }

    @Test
    fun `a client that wraps the cache is reported unreadable and shows nothing`() = runTest {
        val policy = testPolicy()
        val wrapper = object : SwrClient by SwrCache(policy) {}

        val cache = InspectedSoilCache(wrapper, policy)

        assertFalse(cache.coverage.isClientReadable)
        assertTrue(cache.readRecords().isEmpty())
    }

    @Test
    fun `a query that appears after the snapshot is reported with a greater revision`() = runTest {
        val policy = testPolicy()
        val client = SwrCache(policy)
        val reporter = SoilCacheReporter(InspectedSoilCache(client, policy), SoilEntryHandles())
        val snapshot = reporter.takeSnapshot()
        val events = mutableListOf<SoilEntriesChanged>()
        backgroundScope.launch { reporter.reportChanges { events += it } }
        runCurrent()

        client.getQuery(buildQueryKey(QueryId<String>("settings")) { "dark" })
        advanceTimeBy(1_000)

        val event = events.single()
        assertEquals(listOf("settings"), event.upserts.map { it.id.namespace })
        assertTrue(event.revision > snapshot.revision)
    }

    private fun TestScope.testPolicy(): SwrCachePolicy = SwrCachePolicy(
        coroutineScope = backgroundScope,
        mainDispatcher = UnconfinedTestDispatcher(testScheduler),
        queryCache = QueryCacheBuilder {
            put(cachedProfileId, "Ada")
            put(cachedFeedId, listOf(QueryChunk(data = "first page", param = 1)))
        },
    )
}
