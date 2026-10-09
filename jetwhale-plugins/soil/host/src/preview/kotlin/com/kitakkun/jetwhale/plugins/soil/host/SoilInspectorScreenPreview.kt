package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.json.Json
import java.time.ZoneOffset

/** The app's clock in the fixtures below, in epoch seconds as Soil keeps it. */
internal const val FIXTURE_NOW = 1_791_021_720L

private const val FIXTURE_NOW_MILLIS = FIXTURE_NOW * 1000

internal val FixtureTimeOfDayFormatter = TimeOfDayFormatter(ZoneOffset.UTC)

internal val FixtureCoverage = SoilCacheCoverage(clientClassName = "SwrCachePlus", isClientReadable = true, includesInactiveEntries = true, includesSubscriptions = true)

private val QueryOptions = mapOf(
    "staleTime" to "1m",
    "gcTime" to "5m",
    "keepAliveTime" to "5s",
    "revalidateOnReconnect" to "true",
    "revalidateOnFocus" to "true",
    "retryCount" to "3",
)

internal val FixtureUserQuery = ListedSoilEntry(
    entry = SoilEntry(
        handle = "query-1",
        kind = SoilEntryKind.QUERY,
        location = SoilEntryLocation.ACTIVE,
        id = SoilEntryId(className = "QueryId", namespace = "users/profile", tags = listOf("42")),
        state = queryState(replyUpdatedAt = FIXTURE_NOW - 11, staleAt = FIXTURE_NOW + 49, fetchStatus = SoilFetchStatus.Idle),
        isObserved = true,
        options = QueryOptions,
        replyRevision = 3,
        inactiveSinceEpochMillis = null,
        inFlightSinceEpochMillis = null,
        chunkParams = null,
    ),
    isGone = false,
)

internal val FixtureRenameMutation = ListedSoilEntry(
    entry = SoilEntry(
        handle = "mutation-5",
        kind = SoilEntryKind.MUTATION,
        location = SoilEntryLocation.ACTIVE,
        id = SoilEntryId(className = "MutationId", namespace = "users/rename", tags = emptyList()),
        state = SoilEntryState.Mutation(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = FIXTURE_NOW - 12, error = null, errorUpdatedAt = 0, mutatedCount = 2, submittedAt = FIXTURE_NOW - 12),
        isObserved = true,
        options = mapOf("isOneShot" to "false", "isStrictMode" to "false", "retryCount" to "3"),
        replyRevision = 2,
        inactiveSinceEpochMillis = null,
        inFlightSinceEpochMillis = null,
        chunkParams = null,
    ),
    isGone = false,
)

internal val FixtureFailedQuery = ListedSoilEntry(
    entry = FixtureUserQuery.entry.copy(
        handle = "query-6",
        id = SoilEntryId(className = "QueryId", namespace = "orders/recent", tags = emptyList()),
        state = queryState(replyUpdatedAt = FIXTURE_NOW - 420, staleAt = FIXTURE_NOW - 360, fetchStatus = SoilFetchStatus.Idle).copy(
            status = SoilStatus.FAILURE,
            error = SoilEntryError(className = "IOException", message = "Connection reset"),
            errorUpdatedAt = FIXTURE_NOW - 3,
        ),
    ),
    isGone = false,
)

internal val FixtureEntries: List<ListedSoilEntry> = listOf(
    FixtureUserQuery,
    ListedSoilEntry(
        entry = FixtureUserQuery.entry.copy(
            handle = "query-2",
            id = SoilEntryId(className = "QueryId", namespace = "settings", tags = emptyList()),
            state = queryState(replyUpdatedAt = FIXTURE_NOW - 300, staleAt = FIXTURE_NOW - 240, fetchStatus = SoilFetchStatus.Fetching(isValidating = true)),
            inFlightSinceEpochMillis = FIXTURE_NOW_MILLIS - 1_200,
        ),
        isGone = false,
    ),
    ListedSoilEntry(
        entry = FixtureUserQuery.entry.copy(
            handle = "query-3",
            location = SoilEntryLocation.INACTIVE,
            id = SoilEntryId(className = "QueryId", namespace = "users/profile", tags = listOf("7")),
            state = queryState(replyUpdatedAt = FIXTURE_NOW - 600, staleAt = FIXTURE_NOW - 540, fetchStatus = SoilFetchStatus.Idle).copy(isInvalidated = true),
            isObserved = false,
            replyRevision = 0,
            inactiveSinceEpochMillis = FIXTURE_NOW_MILLIS - 80_000,
        ),
        isGone = false,
    ),
    FixtureFailedQuery,
    ListedSoilEntry(
        entry = FixtureUserQuery.entry.copy(
            handle = "infinite-query-4",
            kind = SoilEntryKind.INFINITE_QUERY,
            id = SoilEntryId(className = "InfiniteQueryId", namespace = "posts/feed", tags = emptyList()),
            state = queryState(replyUpdatedAt = FIXTURE_NOW - 30, staleAt = FIXTURE_NOW + 270, fetchStatus = SoilFetchStatus.Idle),
            chunkParams = listOf("0", "1", "2"),
        ),
        isGone = false,
    ),
    FixtureRenameMutation,
    ListedSoilEntry(
        entry = FixtureRenameMutation.entry.copy(
            handle = "mutation-7",
            id = SoilEntryId(className = "MutationId", namespace = "auto/3f2c9a51", tags = emptyList()),
            state = SoilEntryState.Mutation(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = FIXTURE_NOW - 95, error = null, errorUpdatedAt = 0, mutatedCount = 1, submittedAt = FIXTURE_NOW - 95),
            isObserved = false,
        ),
        isGone = true,
    ),
    ListedSoilEntry(
        entry = SoilEntry(
            handle = "subscription-8",
            kind = SoilEntryKind.SUBSCRIPTION,
            location = SoilEntryLocation.ACTIVE,
            id = SoilEntryId(className = "SubscriptionId", namespace = "clock/ticks", tags = emptyList()),
            state = SoilEntryState.Subscription(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = FIXTURE_NOW - 1, error = null, errorUpdatedAt = 0, restartedAt = 0),
            isObserved = true,
            options = emptyMap(),
            replyRevision = 40,
            inactiveSinceEpochMillis = null,
            inFlightSinceEpochMillis = null,
            chunkParams = null,
        ),
        isGone = false,
    ),
)

/** A tap that renamed the user, the profile refetching after it, a failed fetch and a refresh in flight. */
internal val FixtureEvents: List<SoilEvent> = listOf(
    fixtureEvent(1, FixtureRenameMutation, millisAgo = 12_000, SoilEventKind.MUTATION_STARTED),
    fixtureEvent(2, FixtureRenameMutation, millisAgo = 11_770, SoilEventKind.MUTATION_SUCCEEDED, durationMillis = 230),
    fixtureEvent(3, FixtureUserQuery, millisAgo = 11_770, SoilEventKind.INVALIDATED),
    fixtureEvent(4, FixtureUserQuery, millisAgo = 11_770, SoilEventKind.FETCH_STARTED, detail = "revalidating its data"),
    fixtureEvent(5, FixtureUserQuery, millisAgo = 11_430, SoilEventKind.FETCH_SUCCEEDED, durationMillis = 340),
    fixtureEvent(6, FixtureFailedQuery, millisAgo = 6_000, SoilEventKind.FETCH_STARTED),
    fixtureEvent(7, FixtureFailedQuery, millisAgo = 3_000, SoilEventKind.FETCH_FAILED, durationMillis = 3_000, detail = "IOException: Connection reset"),
    fixtureEvent(8, FixtureEntries[1], millisAgo = 1_200, SoilEventKind.FETCH_STARTED, detail = "revalidating its data"),
    fixtureEvent(9, FixtureEntries.last(), millisAgo = 1_000, SoilEventKind.SUBSCRIPTION_DATA_RECEIVED),
)

internal val FixtureUserValue = SoilValueLoad.Loaded(
    SoilEntryValue.Json(
        encoding = SoilValueEncoding.CLASS_SERIALIZERS,
        json = Json.parseToJsonElement("""{"id":42,"name":"Ada Lovelace","email":"ada@example.com","roles":["admin","editor"]}"""),
    ),
)

internal val FixtureRenamedUserValue = SoilValueLoad.Loaded(
    SoilEntryValue.Json(encoding = SoilValueEncoding.CLASS_SERIALIZERS, json = Json.parseToJsonElement("""{"id":42,"name":"Grace Hopper"}""")),
)

internal val FixtureExplainer = SoilEntryExplainer(FIXTURE_NOW_MILLIS, FixtureEvents, FixtureTimeOfDayFormatter)

internal val FixtureListFilter = SoilEntryListFilter(FIXTURE_NOW_MILLIS, FixtureEvents.associate { it.handle to it.atEpochMillis })

internal object NoSoilInspectorActions : SoilInspectorActions {
    override fun refresh() = Unit

    override fun select(handle: String) = Unit

    override fun reloadSelectedValue() = Unit

    override fun runActionOnSelected(action: SoilEntryAction) = Unit

    override fun selectEvent(sequence: Long) = Unit

    override fun clearEvents() = Unit

    override fun dismissStatus() = Unit
}

private fun queryState(replyUpdatedAt: Long, staleAt: Long, fetchStatus: SoilFetchStatus) = SoilEntryState.Query(
    status = SoilStatus.SUCCESS,
    hasReply = true,
    replyUpdatedAt = replyUpdatedAt,
    error = null,
    errorUpdatedAt = 0,
    staleAt = staleAt,
    fetchStatus = fetchStatus,
    isInvalidated = false,
)

private fun fixtureEvent(sequence: Long, listed: ListedSoilEntry, millisAgo: Long, kind: SoilEventKind, durationMillis: Long? = null, detail: String? = null) = SoilEvent(
    sequence = sequence,
    atEpochMillis = FIXTURE_NOW_MILLIS - millisAgo,
    handle = listed.entry.handle,
    entryKind = listed.entry.kind,
    entryId = listed.entry.id,
    kind = kind,
    durationMillis = durationMillis,
    detail = detail,
)

/**
 * [SoilInspectorScreen] over the fixtures with [selectedEntry] selected; the docs screenshots use it
 * too. The list takes half the width so that, at a guide page's width, ids stay readable beside
 * their badges.
 */
@Composable
internal fun FixtureSoilInspectorScreen(selectedEntry: ListedSoilEntry?, selectedValue: SoilValueLoad?, selectedEventSequence: Long?, isFollowingEvents: Boolean) {
    SoilInspectorScreen(
        coverage = FixtureCoverage,
        listedEntries = FixtureEntries,
        selectedEntry = selectedEntry,
        selectedValue = selectedValue,
        status = null,
        events = FixtureEvents,
        selectedEventSequence = selectedEventSequence,
        lastActivityEpochMillisByHandle = FixtureEvents.associate { it.handle to it.atEpochMillis },
        listSettings = SoilEntryListSettings.Initial,
        timelineSettings = SoilTimelineSettings.Initial,
        isFollowingEvents = isFollowingEvents,
        agentNowEpochMillis = FIXTURE_NOW_MILLIS,
        timeOfDayFormatter = FixtureTimeOfDayFormatter,
        listSplitPaneState = rememberJwSplitPaneState(0.5f),
        timelineSplitPaneState = rememberJwSplitPaneState(0.64f),
        actions = NoSoilInspectorActions,
        onListSettingsChange = {},
        onTimelineSettingsChange = {},
        onFollowingEventsChange = {},
    )
}

@Preview
@Composable
private fun SoilInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        FixtureSoilInspectorScreen(selectedEntry = FixtureUserQuery, selectedValue = FixtureUserValue, selectedEventSequence = null, isFollowingEvents = true)
    }
}

@Preview
@Composable
private fun SoilInspectorScreenUnsupportedClientPreview() {
    JwTheme(darkTheme = false) {
        SoilInspectorScreen(
            coverage = FixtureCoverage.copy(clientClassName = "LoggingSwrClient", isClientReadable = false),
            listedEntries = emptyList(),
            selectedEntry = null,
            selectedValue = null,
            status = null,
            events = emptyList(),
            selectedEventSequence = null,
            lastActivityEpochMillisByHandle = emptyMap(),
            listSettings = SoilEntryListSettings.Initial,
            timelineSettings = SoilTimelineSettings.Initial,
            isFollowingEvents = true,
            agentNowEpochMillis = FIXTURE_NOW_MILLIS,
            timeOfDayFormatter = FixtureTimeOfDayFormatter,
            listSplitPaneState = rememberJwSplitPaneState(0.44f),
            timelineSplitPaneState = rememberJwSplitPaneState(0.64f),
            actions = NoSoilInspectorActions,
            onListSettingsChange = {},
            onTimelineSettingsChange = {},
            onFollowingEventsChange = {},
        )
    }
}

@Preview
@Composable
private fun SoilEntryListPaneProblemsPreview() {
    JwTheme(darkTheme = true) {
        SoilEntryListPane(
            listedEntries = FixtureEntries,
            selectedHandle = FixtureFailedQuery.entry.handle,
            settings = SoilEntryListSettings.Initial.copy(conditions = setOf(SoilEntryCondition.FAILED, SoilEntryCondition.STALE), sort = SoilEntrySort.RECENT_ACTIVITY),
            listFilter = FixtureListFilter,
            agentNowEpochSeconds = FIXTURE_NOW,
            onSettingsChange = {},
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun SoilEntryDetailPaneMutationPreview() {
    JwTheme(darkTheme = false) {
        SoilEntryDetailPane(
            listed = FixtureRenameMutation,
            notes = FixtureExplainer.notesOn(FixtureRenameMutation),
            recentEvents = FixtureExplainer.recentEventsOf(FixtureRenameMutation.entry.handle, limit = 8),
            followUps = FixtureExplainer.followUpsOf(FixtureRenameMutation.entry.handle),
            value = FixtureRenamedUserValue,
            agentNowEpochSeconds = FIXTURE_NOW,
            timeOfDayFormatter = FixtureTimeOfDayFormatter,
            onRunAction = {},
            onReloadValue = {},
            onSelectEvent = {},
        )
    }
}

@Preview
@Composable
private fun SoilEntryDetailPaneFailedQueryPreview() {
    JwTheme(darkTheme = true) {
        SoilEntryDetailPane(
            listed = FixtureFailedQuery,
            notes = FixtureExplainer.notesOn(FixtureFailedQuery),
            recentEvents = FixtureExplainer.recentEventsOf(FixtureFailedQuery.entry.handle, limit = 8),
            followUps = null,
            value = SoilValueLoad.Failed("Failed to reach the app: closed"),
            agentNowEpochSeconds = FIXTURE_NOW,
            timeOfDayFormatter = FixtureTimeOfDayFormatter,
            onRunAction = {},
            onReloadValue = {},
            onSelectEvent = {},
        )
    }
}

@Preview
@Composable
private fun SoilTimelinePanePreview() {
    JwTheme(darkTheme = false) {
        SoilTimelinePane(
            events = FixtureEvents,
            selectedEventSequence = 5,
            selectedHandle = FixtureUserQuery.entry.handle,
            settings = SoilTimelineSettings.Initial.copy(categories = setOf(SoilEventCategory.FETCHES, SoilEventCategory.INVALIDATIONS)),
            isFollowing = false,
            timeOfDayFormatter = FixtureTimeOfDayFormatter,
            onSettingsChange = {},
            onFollowingChange = {},
            onSelectEvent = {},
            onClear = {},
        )
    }
}

@Preview
@Composable
private fun SoilValueViewToStringPreview() {
    JwTheme(darkTheme = false) {
        SoilValueView(
            SoilValueLoad.Loaded(
                SoilEntryValue.Text(encoding = SoilValueEncoding.TO_STRING, text = "Page(items=[Post(id=1, title=Hello)], next=2)", fullLength = 46),
            ),
        )
    }
}
