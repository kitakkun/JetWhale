package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SoilInspectorScreenTest {
    private val profile = ListedSoilEntry(queryEntry(handle = "query-1", namespace = "users/profile", staleAt = HOST_NOW + 60), isGone = false)
    private val failedSettingsQuery = ListedSoilEntry(queryEntry(handle = "query-3", namespace = "settings", status = SoilStatus.FAILURE), isGone = false)
    private val renameMutation = ListedSoilEntry(mutationEntry(handle = "mutation-2", namespace = "users/rename"), isGone = true)
    private val events = listOf(eventOf(1, failedSettingsQuery.entry, SoilEventKind.FETCH_FAILED))

    private val ranActions = mutableListOf<SoilEntryAction>()
    private val selectedEventSequences = mutableListOf<Long>()
    private val selectedHandles = mutableListOf<String>()

    @Test
    fun `an action that does not apply is disabled and says why`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = profile) }

        onNode(hasText("Remove") and hasClickAction()).assertIsNotEnabled()
        onNode(hasText("Remove: The entry is active, and active entries are not removed.")).assertExists()
        onNode(hasText("Invalidate") and hasClickAction()).assertIsEnabled().performClick()

        assertEquals(listOf(SoilEntryAction.INVALIDATE), ranActions)
    }

    @Test
    fun `a gone mutation shows its last state without actions`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = renameMutation) }

        onNode(hasText("Soil dropped this mutation", substring = true)).assertExists()
        onNode(hasText("Invalidate") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `the problems banner narrows the list to the entries in trouble`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = null) }

        onNode(hasText("Needs a look: 1 failed")).assertExists()
        onNode(hasText("Show them") and hasClickAction()).performClick()

        onNode(hasText("settings") and hasText("Failure")).assertExists()
        onNode(hasText("users/profile")).assertDoesNotExist()
        onNode(hasText("Showing 1 of 3")).assertExists()
    }

    @Test
    fun `clearing the filters lists every entry again`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = null) }
        onNode(hasText("Failed 1") and hasClickAction()).performClick()
        onNode(hasText("users/profile")).assertDoesNotExist()

        onNode(hasText("Clear filters") and hasClickAction()).performClick()

        onNode(hasText("users/profile")).assertExists()
        onNode(hasText("Showing", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `an event in the timeline selects itself`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = null) }

        onNode(hasText("Fetch failed")).performClick()

        assertEquals(listOf(1L), selectedEventSequences)
    }

    @Test
    fun `down moves the selection to the next entry in the list`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = profile) }
        onNode(hasText("users/profile") and hasClickAction()).performClick()

        onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        assertEquals(listOf(profile.entry.handle, failedSettingsQuery.entry.handle), selectedHandles)
    }

    @Test
    fun `a client the agent cannot read is named`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage.copy(clientClassName = "LoggingSwrClient", isClientReadable = false), selectedEntry = null) }

        onNode(hasText("Unsupported client")).assertExists()
        onNode(hasText("LoggingSwrClient", substring = true)).assertExists()
    }

    @Test
    fun `without the policy the screen says only active entries are shown`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage.copy(includesInactiveEntries = false), selectedEntry = profile) }

        onNode(hasText("Only active entries are shown", substring = true)).assertExists()
    }

    @Composable
    private fun Screen(coverage: SoilCacheCoverage, selectedEntry: ListedSoilEntry?) {
        var listSettings by remember { mutableStateOf(SoilEntryListSettings.Initial) }
        JwTheme(darkTheme = false) {
            SoilInspectorScreen(
                coverage = coverage,
                listedEntries = listOf(profile, failedSettingsQuery, renameMutation),
                selectedEntry = selectedEntry,
                selectedValue = SoilValueLoad.Loaded(SoilEntryValue.NoReply),
                status = null,
                events = events,
                selectedEventSequence = null,
                lastActivityEpochMillisByHandle = emptyMap(),
                listSettings = listSettings,
                timelineSettings = SoilTimelineSettings.Initial,
                isFollowingEvents = true,
                agentNowEpochMillis = HOST_NOW * 1000,
                timeOfDayFormatter = TimeOfDayFormatter(ZoneOffset.UTC),
                listSplitPaneState = rememberJwSplitPaneState(0.42f),
                timelineSplitPaneState = rememberJwSplitPaneState(0.62f),
                actions = object : SoilInspectorActions {
                    override fun refresh() = Unit

                    override fun select(handle: String) {
                        selectedHandles += handle
                    }

                    override fun reloadSelectedValue() = Unit

                    override fun runActionOnSelected(action: SoilEntryAction) {
                        ranActions += action
                    }

                    override fun selectEvent(sequence: Long) {
                        selectedEventSequences += sequence
                    }

                    override fun clearEvents() = Unit

                    override fun dismissStatus() = Unit
                },
                onListSettingsChange = { listSettings = it },
                onTimelineSettingsChange = {},
                onFollowingEventsChange = {},
            )
        }
    }
}
