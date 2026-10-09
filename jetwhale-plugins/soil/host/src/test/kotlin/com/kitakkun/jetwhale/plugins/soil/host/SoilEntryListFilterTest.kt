package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SoilEntryListFilterTest {
    private val staleProfile = listed(queryEntry(handle = "query-1", namespace = "users/profile", replyUpdatedAt = 900, staleAt = 950))
    private val freshFeed = listed(queryEntry(handle = "query-2", namespace = "posts/feed", replyUpdatedAt = 990, staleAt = 1_050))
    private val cachedProfile = listed(queryEntry(handle = "query-3", namespace = "users/profile", location = SoilEntryLocation.INACTIVE, replyUpdatedAt = 800, staleAt = 1_050, isObserved = false))
    private val failedSettingsQuery = listed(queryEntry(handle = "query-4", namespace = "settings", status = SoilStatus.FAILURE, replyUpdatedAt = 0))
    private val goneRename = ListedSoilEntry(mutationEntry(handle = "mutation-5", namespace = "users/rename"), isGone = true)
    private val entries = listOf(staleProfile, freshFeed, cachedProfile, failedSettingsQuery, goneRename)

    private val listFilter = SoilEntryListFilter(agentNowEpochMillis = HOST_NOW * 1000, lastActivityEpochMillisByHandle = mapOf("query-2" to 999_000L, "query-4" to 995_000L))

    @Test
    fun `conditions in one group widen the list and conditions in different groups narrow it`() {
        val widened = listFilter.shownEntriesOf(entries, settingsOf(SoilEntryCondition.STALE, SoilEntryCondition.FAILED))
        val narrowed = listFilter.shownEntriesOf(entries, settingsOf(SoilEntryCondition.STALE, SoilEntryCondition.FAILED, SoilEntryCondition.INACTIVE))

        assertEquals(listOf(staleProfile, failedSettingsQuery), widened)
        assertEquals(emptyList(), narrowed)
    }

    @Test
    fun `the search matches namespaces and tags whatever the case`() {
        val tagged = listed(queryEntry(handle = "query-6", namespace = "orders", isObserved = true).copy(id = SoilEntryId(className = "QueryId", namespace = "orders", tags = listOf("Profile-7"))))

        val shown = listFilter.shownEntriesOf(entries + tagged, SoilEntryListSettings.Initial.copy(searchText = "PROFILE"))

        assertEquals(listOf(staleProfile, cachedProfile, tagged), shown)
    }

    @Test
    fun `a count covers the entries matching the search`() {
        val profileCounts = listOf(SoilEntryCondition.OBSERVED, SoilEntryCondition.INACTIVE, SoilEntryCondition.FAILED).map { listFilter.countMeeting(entries, searchText = "profile", condition = it) }

        assertEquals(listOf(1, 1, 0), profileCounts)
        assertEquals(1, listFilter.countMeeting(entries, searchText = "", condition = SoilEntryCondition.FAILED))
    }

    @Test
    fun `a gone mutation is neither active nor fetching`() {
        assertEquals(false, listFilter.meets(goneRename, SoilEntryCondition.ACTIVE))
        assertEquals(true, listFilter.meets(goneRename, SoilEntryCondition.GONE))
    }

    @Test
    fun `sorting by namespace orders by namespace and then by tags`() {
        val shown = listFilter.shownEntriesOf(entries, SoilEntryListSettings.Initial.copy(sort = SoilEntrySort.NAMESPACE))

        assertEquals(listOf("posts/feed", "settings", "users/profile", "users/profile", "users/rename"), shown.map { it.entry.id.namespace })
    }

    @Test
    fun `sorting by last updated puts the newest reply or error first`() {
        val shown = listFilter.shownEntriesOf(listOf(staleProfile, freshFeed, cachedProfile), SoilEntryListSettings.Initial.copy(sort = SoilEntrySort.LAST_UPDATED))

        assertEquals(listOf(freshFeed, staleProfile, cachedProfile), shown)
    }

    @Test
    fun `sorting by recent activity puts the entry with the latest event first and falls back on its last update`() {
        val shown = listFilter.shownEntriesOf(listOf(staleProfile, freshFeed, failedSettingsQuery, goneRename), SoilEntryListSettings.Initial.copy(sort = SoilEntrySort.RECENT_ACTIVITY))

        assertEquals(listOf(freshFeed, failedSettingsQuery, goneRename, staleProfile), shown)
    }

    @Test
    fun `a fetch running over ten seconds is a problem told among all that are running`() {
        val slow = listed(fetching(queryEntry(handle = "query-7", namespace = "slow"), inFlightSinceEpochMillis = HOST_NOW * 1000 - 12_000))
        val quick = listed(fetching(queryEntry(handle = "query-8", namespace = "quick"), inFlightSinceEpochMillis = HOST_NOW * 1000 - 2_000))

        val problems = listFilter.problemsOf(listOf(slow, quick, failedSettingsQuery))

        assertEquals(listOf("1 failed", "2 running, 1 of them over 10s"), problems.map(SoilProblem::text))
        assertEquals(listOf(SoilEntryCondition.FAILED, SoilEntryCondition.FETCHING), problems.map(SoilProblem::condition))
    }

    private fun settingsOf(vararg conditions: SoilEntryCondition) = SoilEntryListSettings.Initial.copy(conditions = conditions.toSet())

    private fun listed(entry: SoilEntry) = ListedSoilEntry(entry, isGone = false)

    private fun fetching(entry: SoilEntry, inFlightSinceEpochMillis: Long) = entry.copy(
        state = (entry.state as SoilEntryState.Query).copy(fetchStatus = SoilFetchStatus.Fetching(isValidating = true)),
        inFlightSinceEpochMillis = inFlightSinceEpochMillis,
    )
}
