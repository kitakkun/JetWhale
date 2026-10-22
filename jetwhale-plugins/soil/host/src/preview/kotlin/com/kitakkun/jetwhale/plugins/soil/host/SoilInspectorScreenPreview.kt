package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.json.Json

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
