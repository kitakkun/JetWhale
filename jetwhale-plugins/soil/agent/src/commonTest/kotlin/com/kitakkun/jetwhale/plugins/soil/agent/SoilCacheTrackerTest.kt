package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import soil.query.InfiniteQueryId
import soil.query.MutationId
import soil.query.MutationState
import soil.query.MutationStatus
import soil.query.QueryChunk
import soil.query.QueryFetchStatus
import soil.query.QueryId
import soil.query.QueryState
import soil.query.QueryStatus
import soil.query.core.DataModel
import soil.query.core.Reply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoilCacheTrackerTest {
    private val handles = SoilEntryHandles()
    private val clock = SteppedClock(nowEpochMillis = 1_000_000)
    private val tracker = SoilCacheTracker(handles, clock)

    private val profileKey = SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("users/profile", 42))
    private val renameMutationKey = SoilEntryKey(SoilEntryKind.MUTATION, MutationId<Unit, String>("users/rename"))

    @Test
    fun `the first reading reports every entry`() {
        val changes = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test<String>(status = QueryStatus.Pending)), record(renameMutationKey, MutationState.test<Unit>())))

        assertEquals(listOf("users/profile", "users/rename"), changes.upserts.map { it.id.namespace })
        assertEquals(1, changes.revision)
    }

    @Test
    fun `a reading with the same state objects reports nothing and keeps the revision`() {
        val records = listOf(record(profileKey, QueryState.test<String>()))
        tracker.replaceEntriesWith(records)

        val changes = tracker.replaceEntriesWith(records)

        assertTrue(changes.isEmpty)
        assertEquals(1, changes.revision)
    }

    @Test
    fun `a new state object with the same fields and reply reports nothing`() {
        val reply = Reply.some("Ada")
        tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = reply, replyUpdatedAt = 100))))

        val changes = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = reply, replyUpdatedAt = 100, fetchStatus = QueryFetchStatus.Idle))))

        assertTrue(changes.isEmpty)
    }

    @Test
    fun `a changed state is reported again under the same handle`() {
        val first = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test<String>(status = QueryStatus.Pending)))).upserts.single()

        val second = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = Reply.some("Ada"), status = QueryStatus.Success)))).upserts.single()

        assertEquals(first.handle, second.handle)
        assertEquals(SoilStatus.SUCCESS, second.state.status)
        assertEquals(2, tracker.revision)
    }

    @Test
    fun `a new reply within the same second is reported with a new reply revision`() {
        val first = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = Reply.some("Ada"), replyUpdatedAt = 100, status = QueryStatus.Success)))).upserts.single()

        val second = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = Reply.some("Grace"), replyUpdatedAt = 100, status = QueryStatus.Success)))).upserts.single()

        assertEquals(first.state, second.state)
        assertEquals(first.replyRevision + 1, second.replyRevision)
    }

    @Test
    fun `an entry moving from the store to the cache is reported with its new location`() {
        val state = QueryState.test(reply = Reply.some("Ada"), status = QueryStatus.Success)
        tracker.replaceEntriesWith(listOf(record(profileKey, state)))

        val moved = tracker.replaceEntriesWith(listOf(record(profileKey, state, location = SoilEntryLocation.INACTIVE, isObserved = false))).upserts.single()

        assertEquals(SoilEntryLocation.INACTIVE, moved.location)
    }

    @Test
    fun `an entry that is no longer read is reported removed`() {
        val handle = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test<String>()), record(renameMutationKey, MutationState.test<Unit>()))).upserts.first().handle

        val changes = tracker.replaceEntriesWith(listOf(record(renameMutationKey, MutationState.test<Unit>())))

        assertEquals(listOf(handle), changes.removedHandles)
        assertEquals(listOf("users/rename"), tracker.entries.map { it.id.namespace })
    }

    @Test
    fun `a query state is put on the wire field by field`() {
        val state = QueryState.test(
            reply = Reply.some("Ada"),
            replyUpdatedAt = 100,
            error = IllegalStateException("offline"),
            errorUpdatedAt = 120,
            staleAt = 160,
            status = QueryStatus.Failure,
            fetchStatus = QueryFetchStatus.Fetching(isValidating = true),
            isInvalidated = true,
        )

        val entry = tracker.replaceEntriesWith(listOf(record(profileKey, state))).upserts.single()

        assertEquals(SoilEntryId(className = "QueryId", namespace = "users/profile", tags = listOf("42")), entry.id)
        assertEquals(
            SoilEntryState.Query(
                status = SoilStatus.FAILURE,
                hasReply = true,
                replyUpdatedAt = 100,
                error = SoilEntryError(className = "IllegalStateException", message = "offline"),
                errorUpdatedAt = 120,
                staleAt = 160,
                fetchStatus = SoilFetchStatus.Fetching(isValidating = true),
                isInvalidated = true,
            ),
            entry.state,
        )
    }

    @Test
    fun `an entry moving into the cache keeps its options and says when it moved`() {
        val state = QueryState.test(reply = Reply.some("Ada"), status = QueryStatus.Success)
        tracker.replaceEntriesWith(listOf(record(profileKey, state, options = mapOf("gcTime" to "5m"))))
        clock.nowEpochMillis += 5_000

        val cached = tracker.replaceEntriesWith(listOf(record(profileKey, state, location = SoilEntryLocation.INACTIVE, isObserved = false))).upserts.single()

        assertEquals(mapOf("gcTime" to "5m"), cached.options)
        assertEquals(1_005_000, cached.inactiveSinceEpochMillis)
    }

    @Test
    fun `a fetch that starts after the first reading carries when it started`() {
        tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = Reply.some("Ada"), status = QueryStatus.Success))))
        clock.nowEpochMillis += 2_000

        val fetching = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = Reply.some("Ada"), status = QueryStatus.Success, fetchStatus = QueryFetchStatus.Fetching(isValidating = true))))).upserts.single()

        assertEquals(1_002_000, fetching.inFlightSinceEpochMillis)
    }

    @Test
    fun `a fetch already running when the entry is first seen counts from that reading and gets no start event`() {
        val changes = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test<String>(fetchStatus = QueryFetchStatus.Fetching(isValidating = false)))))

        assertEquals(1_000_000, changes.upserts.single().inFlightSinceEpochMillis)
        assertEquals(listOf(SoilEventKind.APPEARED), changes.events.map(SoilEvent::kind))
    }

    @Test
    fun `an entry first seen in the cache has no known time it went there`() {
        val cached = tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test<String>(), location = SoilEntryLocation.INACTIVE, isObserved = false))).upserts.single()

        assertNull(cached.inactiveSinceEpochMillis)
    }

    @Test
    fun `a mutation is recorded before the queries that changed in the same reading`() {
        val reply = Reply.some("Ada")
        tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test(reply = reply, status = QueryStatus.Success)), record(renameMutationKey, MutationState.test<Unit>())))

        val events = tracker.replaceEntriesWith(
            listOf(
                record(profileKey, QueryState.test(reply = reply, status = QueryStatus.Success, isInvalidated = true)),
                record(renameMutationKey, MutationState.test(reply = Reply.some(Unit), status = MutationStatus.Success, mutatedCount = 1)),
            ),
        ).events

        assertEquals(listOf(SoilEventKind.MUTATION_SUCCEEDED, SoilEventKind.INVALIDATED), events.map(SoilEvent::kind))
    }

    @Test
    fun `an entry that is no longer read is recorded as removed`() {
        tracker.replaceEntriesWith(listOf(record(profileKey, QueryState.test<String>())))

        val events = tracker.replaceEntriesWith(emptyList()).events

        assertEquals(listOf(SoilEventKind.REMOVED), events.map(SoilEvent::kind))
    }

    @Test
    fun `an infinite query lists the param of each chunk`() {
        val feedKey = SoilEntryKey(SoilEntryKind.INFINITE_QUERY, InfiniteQueryId<String, Int>("posts/feed"))
        val chunks = listOf(QueryChunk(data = "first", param = 0), QueryChunk(data = "second", param = 1))

        val entry = tracker.replaceEntriesWith(listOf(record(feedKey, QueryState.test(reply = Reply.some(chunks), status = QueryStatus.Success)))).upserts.single()

        assertEquals(listOf("0", "1"), entry.chunkParams)
    }

    @Test
    fun `a mutation state carries its count and when it was submitted`() {
        val entry = tracker.replaceEntriesWith(listOf(record(renameMutationKey, MutationState.test(reply = Reply.some(Unit), replyUpdatedAt = 90, errorUpdatedAt = 95, status = MutationStatus.Success, mutatedCount = 3)))).upserts.single()

        val state = entry.state as SoilEntryState.Mutation
        assertEquals(3, state.mutatedCount)
        assertEquals(95, state.submittedAt)
    }

    private fun record(
        key: SoilEntryKey,
        model: DataModel<*>,
        location: SoilEntryLocation = SoilEntryLocation.ACTIVE,
        isObserved: Boolean = true,
        options: Map<String, String> = emptyMap(),
    ) = SoilCacheRecord(key = key, location = location, model = model, isObserved = isObserved, options = options, stateFlow = null)
}
