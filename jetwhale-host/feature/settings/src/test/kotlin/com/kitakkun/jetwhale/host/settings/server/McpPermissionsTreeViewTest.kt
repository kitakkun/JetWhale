package com.kitakkun.jetwhale.host.settings.server

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tree's input rules. A launch started with `--mcp-allow-all-permissions` reports every tool as
 * allowed no matter what is stored, so a box left editable there takes a click and changes nothing.
 */
@OptIn(ExperimentalTestApi::class)
class McpPermissionsTreeViewTest {
    @Test
    fun `a host group box reports the value it was clicked to`() = runTreeView(isOverriddenForLaunch = false) { calls ->
        onNodeWithText(OBSERVE_LABEL, substring = true).performClick()

        assertEquals(listOf(McpHostToolGroup.OBSERVE to false), calls)
    }

    @Test
    fun `a launch override leaves nothing to click`() = runTreeView(isOverriddenForLaunch = true) { calls ->
        onNodeWithText(OBSERVE_LABEL, substring = true).assertIsNotEnabled()

        // Clicked rather than only asserted disabled: performClick injects a pointer event at the
        // node's center instead of invoking the OnClick semantics action, so a disabled box takes it
        // and does nothing. Dropping the click would leave the test passing on an editable tree.
        onNodeWithText(OBSERVE_LABEL, substring = true).performClick()

        assertTrue(calls.isEmpty(), "an overridden launch cannot store a choice, so it must not take one")
    }
}

/** The first words of `mcp_permission_group_observe`, which is all the selector needs. */
private const val OBSERVE_LABEL = "Observe"

@OptIn(ExperimentalTestApi::class)
private fun runTreeView(
    isOverriddenForLaunch: Boolean,
    body: androidx.compose.ui.test.ComposeUiTest.(calls: List<Pair<McpHostToolGroup, Boolean>>) -> Unit,
) = runComposeUiTest {
    val calls = mutableListOf<Pair<McpHostToolGroup, Boolean>>()
    setContent {
        JwTheme(darkTheme = true) {
            McpPermissionsTreeView(
                uiState = McpPermissionsUiState(
                    allowedHostGroups = setOf(McpHostToolGroup.OBSERVE),
                    plugins = emptyList(),
                    isOverriddenForLaunch = isOverriddenForLaunch,
                ),
                onSetHostGroupAllowed = { group, allowed -> calls += group to allowed },
                onSetPluginInspectAllowed = { _, _ -> },
                onSetPluginInteractAllowed = { _, _ -> },
                onSetPluginToolAllowed = { _, _ -> },
            )
        }
    }
    waitForIdle()
    body(calls)
}
