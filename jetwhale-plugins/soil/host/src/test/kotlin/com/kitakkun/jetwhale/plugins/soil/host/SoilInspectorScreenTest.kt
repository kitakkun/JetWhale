package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SoilInspectorScreenTest {
    private val profile = ListedSoilEntry(queryEntry(handle = "query-1", namespace = "users/profile"), isGone = false)
    private val renameMutation = ListedSoilEntry(mutationEntry(handle = "mutation-2", namespace = "users/rename"), isGone = true)

    @Test
    fun `an action that does not apply is disabled and says why`() = runComposeUiTest {
        val ranActions = mutableListOf<SoilEntryAction>()
        setContent { Screen(coverage = readableCoverage, selectedEntry = profile, onRunAction = { ranActions += it }) }

        onNode(hasText("Remove") and hasClickAction()).assertIsNotEnabled()
        onNode(hasText("Remove: The entry is active, and active entries are not removed.")).assertExists()
        onNode(hasText("Invalidate") and hasClickAction()).assertIsEnabled().performClick()

        assertEquals(listOf(SoilEntryAction.INVALIDATE), ranActions)
    }

    @Test
    fun `a gone mutation shows its last state without actions`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage, selectedEntry = renameMutation, onRunAction = {}) }

        onNode(hasText("Soil dropped this mutation; this is the last state the app reported.")).assertExists()
        onNode(hasText("Invalidate") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `a client the agent cannot read is named`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage.copy(clientClassName = "LoggingSwrClient", isClientReadable = false), selectedEntry = null, onRunAction = {}) }

        onNode(hasText("Unsupported client")).assertExists()
        onNode(hasText("LoggingSwrClient", substring = true)).assertExists()
    }

    @Test
    fun `without the policy the screen says only active entries are shown`() = runComposeUiTest {
        setContent { Screen(coverage = readableCoverage.copy(includesInactiveEntries = false), selectedEntry = profile, onRunAction = {}) }

        onNode(hasText("Only active entries are shown", substring = true)).assertExists()
    }

    @Composable
    private fun Screen(coverage: SoilCacheCoverage, selectedEntry: ListedSoilEntry?, onRunAction: (SoilEntryAction) -> Unit) {
        JwTheme(darkTheme = false) {
            SoilInspectorScreen(
                coverage = coverage,
                listedEntries = listOf(profile, renameMutation),
                selectedEntry = selectedEntry,
                selectedValue = SoilValueLoad.Loaded(SoilEntryValue.NoReply),
                status = null,
                searchQuery = "",
                agentNowEpochSeconds = HOST_NOW,
                actions = object : SoilInspectorActions {
                    override fun refresh() = Unit

                    override fun select(handle: String) = Unit

                    override fun reloadSelectedValue() = Unit

                    override fun runActionOnSelected(action: SoilEntryAction) = onRunAction(action)
                },
                onSearchQueryChange = {},
            )
        }
    }
}
