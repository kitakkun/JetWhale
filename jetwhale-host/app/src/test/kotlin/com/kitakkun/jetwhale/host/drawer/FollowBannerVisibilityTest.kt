package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.McpToolInvocation
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The banner claims the window is following an agent, so it may only appear when the window really
 * moves. These pin it to the same cases `FollowAiOperationService` acts on.
 */
class FollowBannerVisibilityTest {

    private val nothingPoppedOut: (String, String) -> Boolean = { _, _ -> false }

    private fun invocation(pluginId: String?, sessionId: String?) = McpToolInvocation(
        id = 1,
        toolName = "jetwhale.click",
        pluginId = pluginId,
        sessionId = sessionId,
        arguments = persistentListOf(),
    )

    @Test
    fun `a call driving a plugin moves the window`() {
        val moves = invocation(pluginId = "plugin-1", sessionId = "session-1")
            .movesTheWindow(selectedSessionId = "session-1", isPluginPoppedOut = nothingPoppedOut)

        assertTrue(moves)
    }

    @Test
    fun `no call in flight moves nothing`() {
        assertFalse(null.movesTheWindow(selectedSessionId = "session-1", isPluginPoppedOut = nothingPoppedOut))
    }

    @Test
    fun `a host call naming no plugin moves nothing`() {
        val moves = invocation(pluginId = null, sessionId = "session-1")
            .movesTheWindow(selectedSessionId = "session-1", isPluginPoppedOut = nothingPoppedOut)

        assertFalse(moves)
    }

    @Test
    fun `a plugin popped out for that session is watched in its own window`() {
        val moves = invocation(pluginId = "plugin-1", sessionId = "session-1")
            .movesTheWindow(
                selectedSessionId = "session-1",
                isPluginPoppedOut = { pluginId, sessionId -> pluginId == "plugin-1" && sessionId == "session-1" },
            )

        assertFalse(moves)
    }

    @Test
    fun `the same plugin popped out for another session still moves the window`() {
        val moves = invocation(pluginId = "plugin-1", sessionId = "session-2")
            .movesTheWindow(
                selectedSessionId = "session-1",
                isPluginPoppedOut = { pluginId, sessionId -> pluginId == "plugin-1" && sessionId == "session-1" },
            )

        assertTrue(moves)
    }

    @Test
    fun `a call naming no session is judged against the drawer's selection`() {
        val poppedOutInSelectedSession: (String, String) -> Boolean = { pluginId, sessionId ->
            pluginId == "plugin-1" && sessionId == "session-1"
        }

        assertFalse(
            invocation(pluginId = "plugin-1", sessionId = null)
                .movesTheWindow(selectedSessionId = "session-1", isPluginPoppedOut = poppedOutInSelectedSession),
        )
        assertTrue(
            invocation(pluginId = "plugin-1", sessionId = null)
                .movesTheWindow(selectedSessionId = "session-2", isPluginPoppedOut = poppedOutInSelectedSession),
        )
    }
}

class FollowBannerPresenceTest {
    private fun state(isAgentConnected: Boolean, isFollowModeOn: Boolean, operatingToolName: String? = null) = AiActivityUiState(
        isAgentConnected = isAgentConnected,
        operatingToolName = operatingToolName,
        isFollowModeOn = isFollowModeOn,
        isFollowingOperation = operatingToolName != null,
    )

    @Test
    fun `the strip is up whenever a follow could happen, call or no call`() {
        assertTrue(state(isAgentConnected = true, isFollowModeOn = true).showsFollowBanner)
        assertTrue(state(isAgentConnected = true, isFollowModeOn = true, operatingToolName = "jetwhale.click").showsFollowBanner)
    }

    @Test
    fun `the strip is down when nothing could move the window`() {
        assertFalse(state(isAgentConnected = false, isFollowModeOn = true).showsFollowBanner)
        assertFalse(state(isAgentConnected = true, isFollowModeOn = false).showsFollowBanner)
        assertFalse(AiActivityUiState.Idle.showsFollowBanner)
    }
}
