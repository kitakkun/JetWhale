package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwSplitPaneState
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The list is a column of ids and badges; the notes, state and value beside it need the room. */
private const val LIST_FRACTION = 0.42f

/** The cache above takes most of the height; the timeline below is read a few rows at a time. */
private const val CACHE_FRACTION = 0.62f

/** Relative times in the UI move on by the second. */
private const val CLOCK_TICK_MILLIS = 1_000L

/** How many of the selected entry's events its detail pane lists. */
private const val RECENT_EVENT_COUNT = 8

/**
 * Binds the live [browser], a ticking clock and what the plugin's storage keeps — the list's and
 * the timeline's settings and the split positions — to [SoilInspectorScreen].
 */
@Composable
internal fun SoilInspectorScreenRoot(browser: SoilCacheBrowser, timeOfDayFormatter: TimeOfDayFormatter, modifier: Modifier = Modifier) {
    var listSettings by rememberPersistent("entryList.settings", SoilEntryListSettings.Initial)
    var timelineSettings by rememberPersistent("timeline.settings", SoilTimelineSettings.Initial)
    var isFollowingEvents by remember { mutableStateOf(true) }
    val agentNowEpochMillis by produceState(browser.agentNowEpochMillis()) {
        while (true) {
            delay(CLOCK_TICK_MILLIS)
            value = browser.agentNowEpochMillis()
        }
    }
    SoilInspectorScreen(
        coverage = browser.coverage,
        listedEntries = browser.listedEntries,
        selectedEntry = browser.selectedEntry,
        selectedValue = browser.selectedValue,
        status = browser.status,
        events = browser.events,
        selectedEventSequence = browser.selectedEventSequence,
        lastActivityEpochMillisByHandle = browser.lastActivityEpochMillisByHandle,
        listSettings = listSettings,
        timelineSettings = timelineSettings,
        isFollowingEvents = isFollowingEvents,
        agentNowEpochMillis = agentNowEpochMillis,
        timeOfDayFormatter = timeOfDayFormatter,
        listSplitPaneState = rememberPersistedSplitPaneState("entryList.splitPosition", LIST_FRACTION),
        timelineSplitPaneState = rememberPersistedSplitPaneState("timeline.splitPosition", CACHE_FRACTION),
        actions = browser,
        onListSettingsChange = { listSettings = it },
        onTimelineSettingsChange = { timelineSettings = it },
        onFollowingEventsChange = { isFollowingEvents = it },
        modifier = modifier,
    )
}

/**
 * A split position the user dragged, kept across host restarts. rememberPersistent loads after the
 * split state exists, so the two are mirrored both ways rather than seeded once.
 */
@Composable
private fun rememberPersistedSplitPaneState(key: String, initialFraction: Float): JwSplitPaneState {
    var storedFraction by rememberPersistent(key, initialFraction)
    val splitPaneState = rememberJwSplitPaneState(initialFraction)
    LaunchedEffect(splitPaneState) {
        launch { snapshotFlow { storedFraction }.collect { splitPaneState.fraction = it } }
        snapshotFlow { splitPaneState.fraction }.collect { storedFraction = it }
    }
    return splitPaneState
}

/**
 * The inspector: the entries and the selected one's detail side by side, problems called out
 * above them, and the timeline of what happened below.
 */
@Composable
internal fun SoilInspectorScreen(
    coverage: SoilCacheCoverage?,
    listedEntries: List<ListedSoilEntry>,
    selectedEntry: ListedSoilEntry?,
    selectedValue: SoilValueLoad?,
    status: SoilBrowserStatus?,
    events: List<SoilEvent>,
    selectedEventSequence: Long?,
    lastActivityEpochMillisByHandle: Map<String, Long>,
    listSettings: SoilEntryListSettings,
    timelineSettings: SoilTimelineSettings,
    isFollowingEvents: Boolean,
    agentNowEpochMillis: Long,
    timeOfDayFormatter: TimeOfDayFormatter,
    listSplitPaneState: JwSplitPaneState,
    timelineSplitPaneState: JwSplitPaneState,
    actions: SoilInspectorActions,
    onListSettingsChange: (SoilEntryListSettings) -> Unit,
    onTimelineSettingsChange: (SoilTimelineSettings) -> Unit,
    onFollowingEventsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listFilter = SoilEntryListFilter(agentNowEpochMillis, lastActivityEpochMillisByHandle)
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Soil Inspector",
            actions = { JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text) },
        )
        status?.let { JwBanner(text = it.message, dismissLabel = "Dismiss", onDismiss = actions::dismissStatus, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        when {
            coverage == null -> JwEmptyState(title = "Waiting for the app", description = "The Soil cache shows up once the app's agent answers.")

            !coverage.isClientReadable -> JwEmptyState(
                title = "Unsupported client",
                description = "The app handed over a ${coverage.clientClassName}. Soil Inspector reads Soil's own SwrCache and SwrCachePlus; pass one of those, not a client that wraps it.",
            )

            else -> {
                if (!coverage.includesInactiveEntries) {
                    JwBanner(text = "Only active entries are shown. Pass the SwrCachePolicy to JetWhaleSoilAgentPlugin to see the inactive ones Soil keeps cached.", tone = JwTone.Info)
                }
                ProblemsBanner(problems = listFilter.problemsOf(listedEntries), onShowProblems = { onListSettingsChange(listSettings.copy(searchText = "", conditions = it)) })
                JwSplitPane(
                    orientation = Orientation.Vertical,
                    state = timelineSplitPaneState,
                    secondMinSize = 120.dp,
                    first = {
                        JwSplitPane(
                            state = listSplitPaneState,
                            first = {
                                SoilEntryListPane(
                                    listedEntries = listedEntries,
                                    selectedHandle = selectedEntry?.entry?.handle,
                                    settings = listSettings,
                                    listFilter = listFilter,
                                    agentNowEpochSeconds = agentNowEpochMillis / 1000,
                                    onSettingsChange = onListSettingsChange,
                                    onSelect = actions::select,
                                )
                            },
                            second = {
                                Box(Modifier.fillMaxSize()) {
                                    if (selectedEntry == null) {
                                        JwEmptyState(title = "Nothing selected", description = "Pick an entry, or an event in the timeline, to see what its state means.")
                                    } else {
                                        val explainer = SoilEntryExplainer(agentNowEpochMillis, events, timeOfDayFormatter)
                                        SoilEntryDetailPane(
                                            listed = selectedEntry,
                                            notes = explainer.notesOn(selectedEntry),
                                            recentEvents = explainer.recentEventsOf(selectedEntry.entry.handle, RECENT_EVENT_COUNT),
                                            followUps = explainer.followUpsOf(selectedEntry.entry.handle),
                                            value = selectedValue,
                                            agentNowEpochSeconds = agentNowEpochMillis / 1000,
                                            timeOfDayFormatter = timeOfDayFormatter,
                                            onRunAction = actions::runActionOnSelected,
                                            onReloadValue = actions::reloadSelectedValue,
                                            onSelectEvent = actions::selectEvent,
                                        )
                                    }
                                }
                            },
                        )
                    },
                    second = {
                        SoilTimelinePane(
                            events = events,
                            selectedEventSequence = selectedEventSequence,
                            selectedHandle = selectedEntry?.entry?.handle,
                            settings = timelineSettings,
                            isFollowing = isFollowingEvents,
                            timeOfDayFormatter = timeOfDayFormatter,
                            onSettingsChange = onTimelineSettingsChange,
                            onFollowingChange = onFollowingEventsChange,
                            onSelectEvent = actions::selectEvent,
                            onClear = actions::clearEvents,
                        )
                    },
                )
            }
        }
    }
}

/** The failures, pauses and slow fetches among the entries, with a button that lists only them. */
@Composable
private fun ProblemsBanner(problems: List<SoilProblem>, onShowProblems: (Set<SoilEntryCondition>) -> Unit) {
    if (problems.isEmpty()) return
    JwBanner(
        text = "Needs a look: ${problems.joinToString(transform = SoilProblem::text)}",
        tone = if (problems.any { it.tone == JwTone.Error }) JwTone.Error else JwTone.Warning,
        actions = { JwButton(text = "Show them", onClick = { onShowProblems(problems.mapTo(mutableSetOf(), SoilProblem::condition)) }, style = JwButtonStyle.Text) },
    )
}

/**
 * Asks the next measure to scroll just far enough to show the item at [index] whole: to the top
 * when it is above the viewport, to the bottom when it is below. A request rather than a scroll, so
 * that it never measures in the middle of a composition.
 */
internal fun LazyListState.requestRevealItem(index: Int) {
    val visibleItems = layoutInfo.visibleItemsInfo
    val firstWhole = visibleItems.firstOrNull { it.offset >= layoutInfo.viewportStartOffset }?.index ?: return requestScrollToItem(index)
    val lastWhole = visibleItems.lastOrNull { it.offset + it.size <= layoutInfo.viewportEndOffset }?.index ?: firstWhole
    when {
        index < firstWhole -> requestScrollToItem(index)
        index > lastWhole -> requestScrollToItem((index - (lastWhole - firstWhole)).coerceAtLeast(0))
    }
}
