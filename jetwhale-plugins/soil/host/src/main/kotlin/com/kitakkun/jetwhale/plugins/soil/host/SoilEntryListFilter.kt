package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import kotlinx.serialization.Serializable

/** A fetch or mutation running longer than this counts as a problem worth a look. */
private const val SLOW_IN_FLIGHT_MILLIS = 10_000L

/** Conditions in the same group widen the list together; conditions from different groups narrow it. */
internal enum class SoilEntryConditionGroup { STATE, LOCATION, OBSERVERS }

/** Something the entry list can be narrowed to. */
@Serializable
internal enum class SoilEntryCondition(val group: SoilEntryConditionGroup) {
    FAILED(SoilEntryConditionGroup.STATE),

    /** A query fetching or a mutation running. */
    FETCHING(SoilEntryConditionGroup.STATE),
    PAUSED(SoilEntryConditionGroup.STATE),
    STALE(SoilEntryConditionGroup.STATE),
    INVALIDATED(SoilEntryConditionGroup.STATE),
    ACTIVE(SoilEntryConditionGroup.LOCATION),
    INACTIVE(SoilEntryConditionGroup.LOCATION),

    /** A mutation Soil dropped, still listed with its last state. */
    GONE(SoilEntryConditionGroup.LOCATION),
    OBSERVED(SoilEntryConditionGroup.OBSERVERS),
    UNOBSERVED(SoilEntryConditionGroup.OBSERVERS),
}

internal val SoilEntryCondition.label: String
    get() = when (this) {
        SoilEntryCondition.FAILED -> "Failed"
        SoilEntryCondition.FETCHING -> "Fetching"
        SoilEntryCondition.PAUSED -> "Paused"
        SoilEntryCondition.STALE -> "Stale"
        SoilEntryCondition.INVALIDATED -> "Invalidated"
        SoilEntryCondition.ACTIVE -> "Active"
        SoilEntryCondition.INACTIVE -> "Inactive"
        SoilEntryCondition.GONE -> "Gone"
        SoilEntryCondition.OBSERVED -> "Observed"
        SoilEntryCondition.UNOBSERVED -> "Unobserved"
    }

/** The tone an entry meeting the condition draws attention with; neutral for the ones that are no concern. */
internal val SoilEntryCondition.tone: JwTone
    get() = when (this) {
        SoilEntryCondition.FAILED -> JwTone.Error
        SoilEntryCondition.FETCHING -> JwTone.Accent
        SoilEntryCondition.PAUSED, SoilEntryCondition.STALE, SoilEntryCondition.INVALIDATED -> JwTone.Warning
        SoilEntryCondition.ACTIVE, SoilEntryCondition.INACTIVE, SoilEntryCondition.GONE, SoilEntryCondition.OBSERVED, SoilEntryCondition.UNOBSERVED -> JwTone.Neutral
    }

/** The order of the entry list. */
@Serializable
internal enum class SoilEntrySort {
    /** Grouped by kind, each group in the order the app first used its entries. */
    KIND,
    NAMESPACE,

    /** Newest reply or error first. */
    LAST_UPDATED,

    /** Latest event first, so what the app touched last is at the top. */
    RECENT_ACTIVITY,
}

internal val SoilEntrySort.label: String
    get() = when (this) {
        SoilEntrySort.KIND -> "Kind"
        SoilEntrySort.NAMESPACE -> "Namespace"
        SoilEntrySort.LAST_UPDATED -> "Last updated"
        SoilEntrySort.RECENT_ACTIVITY -> "Recent activity"
    }

/**
 * How the user narrowed and ordered the entry list; kept across sessions.
 *
 * @property searchText Matched against namespaces and tags, case aside.
 */
@Serializable
internal data class SoilEntryListSettings(
    val searchText: String,
    val conditions: Set<SoilEntryCondition>,
    val sort: SoilEntrySort,
) {
    val isNarrowed: Boolean get() = searchText.isNotBlank() || conditions.isNotEmpty()

    companion object {
        val Initial = SoilEntryListSettings(searchText = "", conditions = emptySet(), sort = SoilEntrySort.KIND)
    }
}

/**
 * What the list calls out above the entries, with the condition that lists them.
 *
 * @property text Says how many entries have the problem, e.g. `2 failed`.
 */
internal data class SoilProblem(val condition: SoilEntryCondition, val text: String, val tone: JwTone)

/**
 * Which entries the list shows and in what order, judged by the app's clock at
 * [agentNowEpochMillis]. The UI and the MCP commands share it, so both list the same entries.
 *
 * @param lastActivityEpochMillisByHandle When each entry last had an event, by the app's clock.
 */
internal class SoilEntryListFilter(
    private val agentNowEpochMillis: Long,
    private val lastActivityEpochMillisByHandle: Map<String, Long>,
) {
    fun meets(listed: ListedSoilEntry, condition: SoilEntryCondition): Boolean {
        val state = listed.entry.state
        return when (condition) {
            SoilEntryCondition.FAILED -> state.status == SoilStatus.FAILURE
            SoilEntryCondition.FETCHING -> !listed.isGone && state.isInFlight
            SoilEntryCondition.PAUSED -> state.isPausedAt(agentNowEpochMillis / 1000)
            SoilEntryCondition.STALE -> state.isStaleAt(agentNowEpochMillis / 1000)
            SoilEntryCondition.INVALIDATED -> state is SoilEntryState.Query && state.isInvalidated
            SoilEntryCondition.ACTIVE -> !listed.isGone && listed.entry.location != SoilEntryLocation.INACTIVE
            SoilEntryCondition.INACTIVE -> listed.entry.location == SoilEntryLocation.INACTIVE
            SoilEntryCondition.GONE -> listed.isGone
            SoilEntryCondition.OBSERVED -> listed.entry.isObserved
            SoilEntryCondition.UNOBSERVED -> !listed.entry.isObserved
        }
    }

    /** The entries [settings] lets through, in its order. */
    fun shownEntriesOf(entries: List<ListedSoilEntry>, settings: SoilEntryListSettings): List<ListedSoilEntry> {
        val conditionGroups = settings.conditions.groupBy(SoilEntryCondition::group).values
        val shown = entries.filter { listed -> matchesSearch(listed, settings.searchText) && conditionGroups.all { group -> group.any { meets(listed, it) } } }
        return when (settings.sort) {
            SoilEntrySort.KIND -> shown.sortedBy { it.entry.kind }
            SoilEntrySort.NAMESPACE -> shown.sortedWith(compareBy({ it.entry.id.namespace }, { it.entry.id.tags.joinToString() }))
            SoilEntrySort.LAST_UPDATED -> shown.sortedByDescending { maxOf(it.entry.state.replyUpdatedAt, it.entry.state.errorUpdatedAt) }
            SoilEntrySort.RECENT_ACTIVITY -> shown.sortedByDescending(::lastActivityEpochMillisOf)
        }
    }

    /** How many of the entries matching [searchText] meet [condition], whatever else is selected. */
    fun countMeeting(entries: List<ListedSoilEntry>, searchText: String, condition: SoilEntryCondition): Int = entries.count { matchesSearch(it, searchText) && meets(it, condition) }

    /** When the entry last had an event, or else when Soil last updated it. */
    fun lastActivityEpochMillisOf(listed: ListedSoilEntry): Long = lastActivityEpochMillisByHandle[listed.entry.handle] ?: (maxOf(listed.entry.state.replyUpdatedAt, listed.entry.state.errorUpdatedAt) * 1000)

    /** Failures, pauses and slow fetches among [entries], the kinds of trouble a list should not hide. */
    fun problemsOf(entries: List<ListedSoilEntry>): List<SoilProblem> = buildList {
        val current = entries.filterNot(ListedSoilEntry::isGone)
        current.count { meets(it, SoilEntryCondition.FAILED) }.takeIf { it > 0 }?.let { add(SoilProblem(SoilEntryCondition.FAILED, "$it failed", JwTone.Error)) }
        current.count { meets(it, SoilEntryCondition.PAUSED) }.takeIf { it > 0 }?.let { add(SoilProblem(SoilEntryCondition.PAUSED, "$it paused after an error", JwTone.Warning)) }
        val slowCount = current.count { listed -> listed.entry.inFlightSinceEpochMillis?.let { agentNowEpochMillis - it > SLOW_IN_FLIGHT_MILLIS } == true }
        if (slowCount > 0) add(SoilProblem(SoilEntryCondition.FETCHING, "$slowCount running over ${SLOW_IN_FLIGHT_MILLIS / 1000}s", JwTone.Warning))
    }

    private fun matchesSearch(listed: ListedSoilEntry, searchText: String): Boolean = searchText.isBlank() ||
        listed.entry.id.namespace.contains(searchText, ignoreCase = true) ||
        listed.entry.id.tags.any { it.contains(searchText, ignoreCase = true) }
}
