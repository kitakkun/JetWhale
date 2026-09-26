package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SidebarWidthTest {
    @Test
    fun `a width outside what the sidebar can use is kept at its bounds`() {
        assertEquals(200.dp, clampSidebarWidth(40.dp))
        assertEquals(480.dp, clampSidebarWidth(2_000.dp))
        assertEquals(320.dp, clampSidebarWidth(320.dp))
    }

    @Test
    fun `dragging the sidebar's edge asks for the width dragged to and saves once at the end`() = runComposeUiTest {
        // The requested width is not fed back during the drag, as a presenter round trip may not
        // arrive between two drag events: each step still has to add to the steps before it.
        var requested = 0.dp
        var saves = 0
        setContent {
            JwTheme(darkTheme = false) {
                ExpandedToolingDrawerView(
                    selectedPluginId = "",
                    plugins = persistentListOf(),
                    hasFailedJars = false,
                    selectedSession = null,
                    sessions = persistentListOf<DebugSession>(),
                    aiActivity = AiActivityUiState.Idle,
                    width = 280.dp,
                    onResize = { requested = it },
                    onResizeFinished = { saves++ },
                    onFollowAiOperationChange = {},
                    onClickShrinkDrawer = {},
                    onClickSettings = {},
                    onClickPluginSettings = {},
                    onClickInfo = {},
                    onOpenMcpTools = {},
                    onOpenAllMcpTools = {},
                    onClickPlugin = {},
                    onClickInactivePlugin = {},
                    onSelectSession = {},
                    onClickPopout = {},
                    isPoppedOut = { false },
                    onClickBringBack = {},
                    onSetPluginEnabled = { _, _ -> },
                )
            }
        }

        onRoot().performMouseInput {
            val edge = centerRight.copy(x = right - 2f)
            moveTo(edge)
            press()
            // Several small steps, each landing before the width comes back: every one must count.
            repeat(6) { moveBy(edge.copy(x = 10f, y = 0f)) }
            release()
        }
        waitForIdle()

        val expected = 280.dp + with(density) { 60f.toDp() }
        assertTrue(requested in expected - 1.dp..expected + 1.dp, "requested $requested, expected $expected")
        assertEquals(1, saves)
    }

    @Test
    fun `the footer stays at the bottom when the plugins above need less than their share`() = runComposeUiTest {
        val session = DebugSession(
            id = "app-1",
            name = "Mac",
            isActive = true,
            transportSecurity = SessionTransportSecurity.LOOPBACK,
            installedPlugins = persistentListOf(),
            appName = "Demo",
            deviceId = "device-1",
            deviceName = "Mac",
        )
        setContent {
            JwTheme(darkTheme = false) {
                Box(modifier = Modifier.height(800.dp)) {
                    ExpandedToolingDrawerView(
                        selectedPluginId = "",
                        plugins = persistentListOf(plugin("Device Mirror", needsApp = false), plugin("Network", needsApp = true)),
                        hasFailedJars = false,
                        selectedSession = session,
                        sessions = persistentListOf(session),
                        aiActivity = AiActivityUiState.Idle,
                        width = 280.dp,
                        onResize = {},
                        onResizeFinished = {},
                        onFollowAiOperationChange = {},
                        onClickShrinkDrawer = {},
                        onClickSettings = {},
                        onClickPluginSettings = {},
                        onClickInfo = {},
                        onOpenMcpTools = {},
                        onOpenAllMcpTools = {},
                        onClickPlugin = {},
                        onClickInactivePlugin = {},
                        onSelectSession = {},
                        onClickPopout = {},
                        isPoppedOut = { false },
                        onClickBringBack = {},
                        onSetPluginEnabled = { _, _ -> },
                    )
                }
            }
        }

        val sidebarBottom = onRoot().getBoundsInRoot().bottom
        val settingsBottom = onNodeWithContentDescription("Settings").getBoundsInRoot().bottom
        assertTrue(sidebarBottom - settingsBottom < 24.dp, "footer ends at $settingsBottom of $sidebarBottom")
    }
}

private fun plugin(name: String, needsApp: Boolean) = DrawerPluginItemUiState(
    name = name,
    id = "com.example.${name.lowercase().replace(' ', '.')}",
    activeIconResource = null,
    inactiveIconResource = null,
    pluginAvailability = PluginAvailability.Enabled,
    underAiControl = false,
    exposesMcpTools = false,
    isHeadless = false,
    needsApp = needsApp,
)
