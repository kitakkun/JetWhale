package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ShrunkToolingDrawerViewTest {
    private val session = DebugSession(
        id = "app-1",
        name = "Sample",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
        appName = "Sample",
        deviceId = "device-1",
        deviceName = "Pixel 9",
    )

    @Test
    fun `the rail shows both rows of a plugin that runs in the host session and in the app, each opening its own session`() = runComposeUiTest {
        val opened = mutableListOf<String?>()
        setContent {
            JwTheme(darkTheme = false) {
                ShrunkToolingDrawerView(
                    plugins = persistentListOf(row(sessionId = HostSession.ID), row(sessionId = session.id)),
                    sessions = persistentListOf(session),
                    selectedSession = session,
                    aiActivity = AiActivityUiState.Idle,
                    onFollowAiOperationChange = {},
                    onOpenMcpSettings = {},
                    onClickExpandMenu = {},
                    onClickSettings = {},
                    onClickPlugin = { opened += it.sessionId },
                    onClickInfo = {},
                    onOpenAllMcpTools = {},
                    onSelectSession = {},
                )
            }
        }

        val buttons = onAllNodesWithContentDescription("Inspector")
        buttons[0].performClick()
        buttons[1].performClick()

        assertEquals(listOf<String?>(HostSession.ID, session.id), opened)
    }

    private fun row(sessionId: String) = DrawerPluginItemUiState(
        name = "Inspector",
        id = "com.example.inspector",
        sessionId = sessionId,
        activeIconResource = null,
        inactiveIconResource = null,
        pluginAvailability = PluginAvailability.Enabled,
        isSelected = false,
        underAiControl = false,
        exposesMcpTools = false,
        isHeadless = false,
    )
}
