package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoilEventLogTest {
    private val eventLog = SoilEventLog()

    private val idleProfile = queryEntry(SoilFetchStatus.Idle)
    private val fetchingProfile = queryEntry(SoilFetchStatus.Fetching(isValidating = false), inFlightSinceEpochMillis = NOW - 300)

    @Test
    fun `an entry seen for the first time appears and one in the cache says so`() {
        val active = eventLog.recordChange(null, idleProfile, NOW).single()
        val cached = eventLog.recordChange(null, idleProfile.copy(location = SoilEntryLocation.INACTIVE, isObserved = false), NOW).single()

        assertEquals(SoilEventKind.APPEARED, active.kind)
        assertNull(active.detail)
        assertEquals("cached", cached.detail)
    }

    @Test
    fun `an entry first seen while fetching appears with no start of its own`() {
        val events = eventLog.recordChange(null, fetchingProfile.copy(inFlightSinceEpochMillis = null), NOW)

        assertEquals(listOf(SoilEventKind.APPEARED), events.map(SoilEvent::kind))
        assertEquals("while fetching", events.single().detail)
    }

    @Test
    fun `a fetch seen starting and ending succeeds with the time since it started`() {
        val started = eventLog.recordChange(idleProfile, fetchingProfile, NOW - 300).single()

        val ended = eventLog.recordChange(fetchingProfile, idleProfile.copy(replyRevision = 1), NOW).single()

        assertEquals(SoilEventKind.FETCH_STARTED, started.kind)
        assertEquals(SoilEventKind.FETCH_SUCCEEDED, ended.kind)
        assertEquals(300, ended.durationMillis)
        assertEquals(started.sequence + 1, ended.sequence)
    }

    @Test
    fun `a fetch that started and ended between two readings is a data update without a duration`() {
        val event = eventLog.recordChange(idleProfile, idleProfile.copy(replyRevision = 1), NOW).single()

        assertEquals(SoilEventKind.DATA_UPDATED, event.kind)
        assertNull(event.durationMillis)
    }

    @Test
    fun `a fetch that ends with the same reply succeeds and says the data did not change`() {
        val ended = eventLog.recordChange(fetchingProfile, idleProfile, NOW).single()

        assertEquals(SoilEventKind.FETCH_SUCCEEDED, ended.kind)
        assertEquals("the data did not change", ended.detail)
    }

    @Test
    fun `a failed fetch carries its error and duration`() {
        val failed = queryEntry(SoilFetchStatus.Idle, status = SoilStatus.FAILURE, error = SoilEntryError("IllegalStateException", "offline"), errorUpdatedAt = 1_000)
        eventLog.recordChange(idleProfile, fetchingProfile, NOW - 300)

        val event = eventLog.recordChange(fetchingProfile, failed, NOW).single()

        assertEquals(SoilEventKind.FETCH_FAILED, event.kind)
        assertEquals("IllegalStateException: offline", event.detail)
        assertEquals(300, event.durationMillis)
    }

    @Test
    fun `a fetch that fails again within the second of the last failure still fails`() {
        val failed = queryEntry(SoilFetchStatus.Idle, status = SoilStatus.FAILURE, error = SoilEntryError("IllegalStateException", "offline"), errorUpdatedAt = 1_000)
        val refetching = failed.copy(state = (failed.state as SoilEntryState.Query).copy(fetchStatus = SoilFetchStatus.Fetching(isValidating = false)))
        eventLog.recordChange(failed, refetching, NOW - 200)

        val event = eventLog.recordChange(refetching, failed, NOW).single()

        assertEquals(SoilEventKind.FETCH_FAILED, event.kind)
    }

    @Test
    fun `a subscription that fails again within the second of its last failure still fails`() {
        val failed = subscriptionEntry(SoilStatus.FAILURE, errorUpdatedAt = 1_000)
        val recovered = subscriptionEntry(SoilStatus.SUCCESS, errorUpdatedAt = 1_000)

        val event = eventLog.recordChange(recovered, failed, NOW).single()

        assertEquals(SoilEventKind.SUBSCRIPTION_FAILED, event.kind)
    }

    @Test
    fun `a fetch already under way when its entry was first seen ends without a duration`() {
        eventLog.recordChange(null, fetchingProfile, NOW - 300)

        val ended = eventLog.recordChange(fetchingProfile, idleProfile.copy(replyRevision = 1), NOW).single()

        assertEquals(SoilEventKind.FETCH_SUCCEEDED, ended.kind)
        assertNull(ended.durationMillis)
    }

    @Test
    fun `a query held back after an error is paused for the seconds left`() {
        val paused = queryEntry(SoilFetchStatus.Paused(unpauseAt = NOW / 1000 + 30))

        val event = eventLog.recordChange(idleProfile, paused, NOW).single()

        assertEquals(SoilEventKind.FETCH_PAUSED, event.kind)
        assertEquals("for 30s", event.detail)
    }

    @Test
    fun `an invalidation comes before the fetch it starts`() {
        val refetching = queryEntry(SoilFetchStatus.Fetching(isValidating = true), isInvalidated = true, inFlightSinceEpochMillis = NOW)

        val events = eventLog.recordChange(idleProfile, refetching, NOW)

        assertEquals(listOf(SoilEventKind.INVALIDATED, SoilEventKind.FETCH_STARTED), events.map(SoilEvent::kind))
        assertEquals("revalidating its data", events.last().detail)
    }

    @Test
    fun `an invalidation is recorded when it is set and not again while it stays set`() {
        val invalidated = queryEntry(SoilFetchStatus.Idle, isInvalidated = true)

        eventLog.recordChange(idleProfile, invalidated, NOW)
        val second = eventLog.recordChange(invalidated, invalidated.copy(isObserved = false), NOW)

        assertEquals(listOf(SoilEventKind.UNOBSERVED), second.map(SoilEvent::kind))
    }

    @Test
    fun `a mutation seen pending succeeds with the time it ran`() {
        val pending = mutationEntry(SoilStatus.PENDING, mutatedCount = 0, inFlightSinceEpochMillis = NOW - 120)
        val started = eventLog.recordChange(mutationEntry(SoilStatus.IDLE, mutatedCount = 0), pending, NOW - 120).single()

        val ended = eventLog.recordChange(pending, mutationEntry(SoilStatus.SUCCESS, mutatedCount = 1), NOW).single()

        assertEquals(SoilEventKind.MUTATION_STARTED, started.kind)
        assertEquals(SoilEventKind.MUTATION_SUCCEEDED, ended.kind)
        assertEquals(120, ended.durationMillis)
    }

    @Test
    fun `a mutation that ran between two readings succeeds without a duration`() {
        val event = eventLog.recordChange(mutationEntry(SoilStatus.SUCCESS, mutatedCount = 1), mutationEntry(SoilStatus.SUCCESS, mutatedCount = 2), NOW).single()

        assertEquals(SoilEventKind.MUTATION_SUCCEEDED, event.kind)
        assertNull(event.durationMillis)
    }

    @Test
    fun `an entry moving into the cache and back is recorded both ways`() {
        val cached = idleProfile.copy(location = SoilEntryLocation.INACTIVE, isObserved = false)

        val left = eventLog.recordChange(idleProfile, cached, NOW)
        val returned = eventLog.recordChange(cached, idleProfile, NOW)

        assertEquals(listOf(SoilEventKind.BECAME_INACTIVE, SoilEventKind.UNOBSERVED), left.map(SoilEvent::kind))
        assertEquals(listOf(SoilEventKind.BECAME_ACTIVE, SoilEventKind.OBSERVED), returned.map(SoilEvent::kind))
    }

    @Test
    fun `the log keeps only the latest five hundred events`() {
        repeat(501) { eventLog.recordChange(null, idleProfile, NOW) }

        assertEquals(500, eventLog.recentEvents.size)
        assertEquals(2, eventLog.recentEvents.first().sequence)
    }

    private fun queryEntry(
        fetchStatus: SoilFetchStatus,
        status: SoilStatus = SoilStatus.SUCCESS,
        error: SoilEntryError? = null,
        errorUpdatedAt: Long = 0,
        isInvalidated: Boolean = false,
        inFlightSinceEpochMillis: Long? = null,
    ) = entry(
        kind = SoilEntryKind.QUERY,
        namespace = "users/profile",
        state = SoilEntryState.Query(
            status = status,
            hasReply = true,
            replyUpdatedAt = 900,
            error = error,
            errorUpdatedAt = errorUpdatedAt,
            staleAt = 960,
            fetchStatus = fetchStatus,
            isInvalidated = isInvalidated,
        ),
        inFlightSinceEpochMillis = inFlightSinceEpochMillis,
    )

    private fun subscriptionEntry(status: SoilStatus, errorUpdatedAt: Long) = entry(
        kind = SoilEntryKind.SUBSCRIPTION,
        namespace = "clock/ticks",
        state = SoilEntryState.Subscription(
            status = status,
            hasReply = true,
            replyUpdatedAt = 900,
            error = SoilEntryError("IllegalStateException", "disconnected"),
            errorUpdatedAt = errorUpdatedAt,
            restartedAt = 0,
        ),
        inFlightSinceEpochMillis = null,
    )

    private fun mutationEntry(status: SoilStatus, mutatedCount: Int, inFlightSinceEpochMillis: Long? = null) = entry(
        kind = SoilEntryKind.MUTATION,
        namespace = "users/rename",
        state = SoilEntryState.Mutation(
            status = status,
            hasReply = mutatedCount > 0,
            replyUpdatedAt = 0,
            error = null,
            errorUpdatedAt = 0,
            mutatedCount = mutatedCount,
            submittedAt = 0,
        ),
        inFlightSinceEpochMillis = inFlightSinceEpochMillis,
    )

    private fun entry(kind: SoilEntryKind, namespace: String, state: SoilEntryState, inFlightSinceEpochMillis: Long?) = SoilEntry(
        handle = "1",
        kind = kind,
        location = SoilEntryLocation.ACTIVE,
        id = SoilEntryId(className = "QueryId", namespace = namespace, tags = emptyList()),
        state = state,
        isObserved = true,
        options = emptyMap(),
        replyRevision = 0,
        inactiveSinceEpochMillis = null,
        inFlightSinceEpochMillis = inFlightSinceEpochMillis,
        chunkParams = null,
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
