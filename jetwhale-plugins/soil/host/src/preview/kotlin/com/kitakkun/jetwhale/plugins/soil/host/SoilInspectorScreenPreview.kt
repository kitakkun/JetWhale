package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryError
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.json.Json

/** The app's clock in the fixtures below. */
internal const val FIXTURE_NOW = 1_791_021_720L

internal val FixtureCoverage = SoilCacheCoverage(clientClassName = "SwrCachePlus", isClientReadable = true, includesInactiveEntries = true, includesSubscriptions = true)

internal val FixtureUserQuery = ListedSoilEntry(
    entry = SoilEntry(
        handle = "query-1",
        kind = SoilEntryKind.QUERY,
        location = SoilEntryLocation.ACTIVE,
        id = SoilEntryId(className = "QueryId", namespace = "users/profile", tags = listOf("42")),
        state = queryState(replyUpdatedAt = FIXTURE_NOW - 75, staleAt = FIXTURE_NOW - 15, fetchStatus = SoilFetchStatus.Idle),
        isObserved = true,
        options = mapOf("staleTime" to "1m", "gcTime" to "5m", "keepAliveTime" to "5s", "retryCount" to "3"),
    ),
    isGone = false,
)

internal val FixtureEntries: List<ListedSoilEntry> = listOf(
    FixtureUserQuery,
    ListedSoilEntry(
        entry = FixtureUserQuery.entry.copy(
            handle = "query-2",
            id = SoilEntryId(className = "QueryId", namespace = "settings", tags = emptyList()),
            state = queryState(replyUpdatedAt = FIXTURE_NOW - 4, staleAt = FIXTURE_NOW + 56, fetchStatus = SoilFetchStatus.Fetching(isValidating = true)),
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
            options = emptyMap(),
        ),
        isGone = false,
    ),
    ListedSoilEntry(
        entry = FixtureUserQuery.entry.copy(
            handle = "infinite-query-4",
            kind = SoilEntryKind.INFINITE_QUERY,
            id = SoilEntryId(className = "Id", namespace = "posts/feed", tags = emptyList()),
            state = queryState(replyUpdatedAt = FIXTURE_NOW - 30, staleAt = FIXTURE_NOW + 270, fetchStatus = SoilFetchStatus.Idle),
        ),
        isGone = false,
    ),
    ListedSoilEntry(
        entry = SoilEntry(
            handle = "mutation-5",
            kind = SoilEntryKind.MUTATION,
            location = SoilEntryLocation.ACTIVE,
            id = SoilEntryId(className = "MutationId", namespace = "auto/3f2c9a51", tags = emptyList()),
            state = SoilEntryState.Mutation(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = FIXTURE_NOW - 12, error = null, errorUpdatedAt = 0, mutatedCount = 2, submittedAt = FIXTURE_NOW - 12),
            isObserved = false,
            options = emptyMap(),
        ),
        isGone = true,
    ),
    ListedSoilEntry(
        entry = SoilEntry(
            handle = "subscription-6",
            kind = SoilEntryKind.SUBSCRIPTION,
            location = SoilEntryLocation.ACTIVE,
            id = SoilEntryId(className = "SubscriptionId", namespace = "clock/ticks", tags = emptyList()),
            state = SoilEntryState.Subscription(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = FIXTURE_NOW - 1, error = null, errorUpdatedAt = 0, restartedAt = 0),
            isObserved = true,
            options = emptyMap(),
        ),
        isGone = false,
    ),
)

internal val FixtureUserValue = SoilValueLoad.Loaded(
    SoilEntryValue.Json(
        encoding = SoilValueEncoding.CLASS_SERIALIZERS,
        json = Json.parseToJsonElement("""{"id":42,"name":"Ada Lovelace","email":"ada@example.com","roles":["admin","editor"]}"""),
    ),
)

internal object NoSoilInspectorActions : SoilInspectorActions {
    override fun refresh() = Unit

    override fun select(handle: String) = Unit

    override fun reloadSelectedValue() = Unit

    override fun runActionOnSelected(action: SoilEntryAction) = Unit
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

@Preview
@Composable
private fun SoilInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        SoilInspectorScreen(
            coverage = FixtureCoverage,
            listedEntries = FixtureEntries,
            selectedEntry = FixtureUserQuery,
            selectedValue = FixtureUserValue,
            status = null,
            query = "",
            agentNowEpochSeconds = FIXTURE_NOW,
            actions = NoSoilInspectorActions,
            onQueryChange = {},
        )
    }
}

@Preview
@Composable
private fun SoilEntryDetailPaneFailedQueryPreview() {
    JwTheme(darkTheme = false) {
        SoilEntryDetailPane(
            listed = FixtureUserQuery.copy(
                entry = FixtureUserQuery.entry.copy(
                    state = queryState(replyUpdatedAt = 0, staleAt = 0, fetchStatus = SoilFetchStatus.Paused(unpauseAt = FIXTURE_NOW + 20)).copy(
                        status = SoilStatus.FAILURE,
                        error = SoilEntryError(className = "IOException", message = "Connection reset"),
                        errorUpdatedAt = FIXTURE_NOW - 3,
                    ),
                    isObserved = false,
                ),
            ),
            value = SoilValueLoad.Loaded(SoilEntryValue.Json(encoding = SoilValueEncoding.CLASS_SERIALIZERS, json = Json.parseToJsonElement("""{"id":42,"name":"Ada Lovelace"}"""))),
            agentNowEpochSeconds = FIXTURE_NOW,
            onRunAction = {},
            onReloadValue = {},
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
            query = "",
            agentNowEpochSeconds = FIXTURE_NOW,
            actions = NoSoilInspectorActions,
            onQueryChange = {},
        )
    }
}
