package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoilEntryExplainerTest {
    private val profile = queryEntry(handle = "query-1", namespace = "users/profile", replyUpdatedAt = 989, staleAt = 1_049).copy(
        options = mapOf("staleTime" to "1m", "gcTime" to "5m", "revalidateOnFocus" to "true", "revalidateOnReconnect" to "false", "retryCount" to "3"),
    )
    private val renameMutation = mutationEntry(handle = "mutation-2", namespace = "users/rename").copy(isObserved = true)

    @Test
    fun `a fresh query says how long it stays fresh and why it is not fetched`() {
        assertEquals(
            "Fresh for another 49s (updated 11s ago, staleTime 1m). Until then Soil answers from the cache without fetching.",
            explainerOf().notesOn(listed(profile)).single().text,
        )
    }

    @Test
    fun `a stale query says Soil refetches only when asked and with which revalidation options`() {
        val stale = profile.copy(state = queryState(profile).copy(replyUpdatedAt = 900, staleAt = 960))

        assertEquals(
            "Stale (updated 1m 40s ago, staleTime 1m). Soil does not refetch on a timer: it refetches stale data when a screen starts observing it, when it is invalidated or resumed, and on focus or reconnect where the app reports them (revalidateOnFocus true, revalidateOnReconnect false).",
            explainerOf().notesOn(listed(stale)).single().text,
        )
    }

    @Test
    fun `a failed query puts the failure first with its retries and how often it failed`() {
        val failed = profile.copy(state = queryState(profile).copy(status = SoilStatus.FAILURE, error = SoilEntryError("IOException", "offline"), errorUpdatedAt = 997))
        val events = listOf(eventOf(1, failed, SoilEventKind.FETCH_FAILED), eventOf(2, failed, SoilEventKind.FETCH_FAILED))

        assertEquals(
            "The last fetch failed 3s ago with IOException: offline. It still holds the data from 11s ago. Soil retries within a fetch, up to 3 times, only for errors the query's shouldRetry accepts (by default those that are Retryable), so the inspector sees the outcome rather than each attempt. It failed 2 times in the timeline.",
            explainerOf(events).notesOn(listed(failed)).first().text,
        )
    }

    @Test
    fun `a paused query says until when by the clockSubscription and that it does not resume by itself`() {
        val paused = profile.copy(state = queryState(profile).copy(fetchStatus = SoilFetchStatus.Paused(unpauseAt = 1_030)))

        assertEquals(
            "Paused after the error until 00:17:10 (in 30s): Soil skips fetches until then, and afterwards fetches again only when a screen observes it, it is invalidated or resumed.",
            explainerOf().notesOn(listed(paused)).first().text,
        )
    }

    @Test
    fun `a cached query says how long it has been unused and when Soil drops it`() {
        val cached = profile.copy(location = SoilEntryLocation.INACTIVE, isObserved = false, inactiveSinceEpochMillis = 920_000)

        assertEquals("Cached and unused for 1m 20s; Soil drops it in 3m 40s (gcTime 5m).", explainerOf().notesOn(listed(cached)).last().text)
    }

    @Test
    fun `a cached query with an infinite gcTime says Soil never drops it`() {
        val cached = profile.copy(location = SoilEntryLocation.INACTIVE, isObserved = false, inactiveSinceEpochMillis = 920_000, options = mapOf("gcTime" to "Infinity"))

        assertEquals("Cached and unused for 1m 20s; Soil never drops it (gcTime Infinity).", explainerOf().notesOn(listed(cached)).last().text)
    }

    @Test
    fun `a query found already cached says when it went inactive is not known`() {
        val cached = profile.copy(location = SoilEntryLocation.INACTIVE, isObserved = false, inactiveSinceEpochMillis = null)

        assertEquals("Cached since before the inspector first read the cache; Soil drops it gcTime (5m) after it became inactive.", explainerOf().notesOn(listed(cached)).last().text)
    }

    @Test
    fun `a mutation names what followed its last run within two seconds`() {
        val events = listOf(
            eventOf(1, renameMutation, SoilEventKind.MUTATION_SUCCEEDED, atEpochMillis = 990_000),
            eventOf(2, profile, SoilEventKind.INVALIDATED, atEpochMillis = 990_000),
            eventOf(3, profile, SoilEventKind.FETCH_STARTED, atEpochMillis = 990_000),
            eventOf(4, profile, SoilEventKind.FETCH_SUCCEEDED, atEpochMillis = 990_400),
            eventOf(5, profile, SoilEventKind.DATA_UPDATED, atEpochMillis = 993_000),
        )

        val explainer = explainerOf(events)

        assertEquals(listOf(2L, 3L, 4L), explainer.followUpsOf(renameMutation.handle)?.events?.map(SoilEvent::sequence))
        assertEquals("Within 2s after the last run: users/profile (invalidated, fetch started, fetch succeeded).", explainer.notesOn(listed(renameMutation)).last().text)
    }

    @Test
    fun `a subscription's values after a run do not count as following it`() {
        val clockSubscription = subscriptionEntry(handle = "subscription-3", namespace = "clock/ticks")
        val events = listOf(eventOf(1, renameMutation, SoilEventKind.MUTATION_SUCCEEDED), eventOf(2, clockSubscription, SoilEventKind.SUBSCRIPTION_DATA_RECEIVED), eventOf(3, clockSubscription, SoilEventKind.SUBSCRIPTION_RESTARTED))

        assertEquals(listOf(3L), explainerOf(events).followUpsOf(renameMutation.handle)?.events?.map(SoilEvent::sequence))
    }

    @Test
    fun `what followed a mutation tells entries with different tags apart`() {
        val profile7 = profile.copy(handle = "query-7", id = profile.id.copy(tags = listOf("7")))
        val profile42 = profile.copy(handle = "query-42", id = profile.id.copy(tags = listOf("42")))
        val events = listOf(
            eventOf(1, renameMutation, SoilEventKind.MUTATION_SUCCEEDED),
            eventOf(2, profile7, SoilEventKind.INVALIDATED),
            eventOf(3, profile42, SoilEventKind.INVALIDATED),
        )

        assertEquals("Within 2s after the last run: users/profile [7] (invalidated); users/profile [42] (invalidated).", explainerOf(events).notesOn(listed(renameMutation)).last().text)
    }

    @Test
    fun `an unobserved invalidated query paused after an error refetches only after the pause`() {
        val pausedInvalidated = profile.copy(
            isObserved = false,
            state = queryState(profile).copy(fetchStatus = SoilFetchStatus.Paused(unpauseAt = 1_030), isInvalidated = true),
        )

        val noteTexts = explainerOf().notesOn(listed(pausedInvalidated)).map(SoilEntryNote::text)

        assertEquals(true, "Invalidated: it refetches when a screen next observes it, once the pause after the error has ended." in noteTexts)
    }

    @Test
    fun `what followed a mutation keeps entries apart that read the same`() {
        val events = listOf(
            eventOf(1, renameMutation, SoilEventKind.MUTATION_SUCCEEDED),
            eventOf(2, profile, SoilEventKind.INVALIDATED),
            eventOf(3, profile.copy(handle = "query-9"), SoilEventKind.FETCH_STARTED),
        )

        assertEquals("Within 2s after the last run: users/profile (invalidated); users/profile (fetch started).", explainerOf(events).notesOn(listed(renameMutation)).last().text)
    }

    @Test
    fun `a mutation no screen observes is dropped rather than cached`() {
        val unobserved = renameMutation.copy(isObserved = false)

        assertEquals(true, explainerOf().notesOn(listed(unobserved)).last().text.endsWith("then drops it: Soil caches no mutations."))
    }

    @Test
    fun `a mutation nothing followed says no query was touched`() {
        val explainer = explainerOf(listOf(eventOf(1, renameMutation, SoilEventKind.MUTATION_SUCCEEDED)))

        assertEquals("No query was invalidated, updated or refetched within 2s after the last run.", explainer.notesOn(listed(renameMutation)).last().text)
    }

    @Test
    fun `a mutation whose run ended before the timeline began has no follow-ups`() {
        assertNull(explainerOf().followUpsOf(renameMutation.handle))
    }

    private fun explainerOf(events: List<SoilEvent> = emptyList()) = SoilEntryExplainer(agentNowEpochMillis = HOST_NOW * 1000, events = events, timeOfDayFormatter = TimeOfDayFormatter(ZoneOffset.UTC))

    private fun listed(entry: SoilEntry) = ListedSoilEntry(entry, isGone = false)

    private fun queryState(entry: SoilEntry) = entry.state as SoilEntryState.Query
}
