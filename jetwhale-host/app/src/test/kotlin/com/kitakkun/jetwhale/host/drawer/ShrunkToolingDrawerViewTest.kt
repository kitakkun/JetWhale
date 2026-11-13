package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ShrunkToolingDrawerViewTest {
    private val appSession = DebugSession(
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
    fun `the rail shows both rows of a plugin that runs in the host session and in the app, each named and opening its own session`() = runComposeUiTest {
        val openedSessionIds = mutableListOf<String?>()
        showRail(persistentListOf(row(sessionId = HostSession.ID), row(sessionId = appSession.id)), onClickPlugin = { openedSessionIds += it.sessionId })

        onNodeWithContentDescription("Inspector · without an app").performClick()
        onNodeWithContentDescription("Inspector · Sample").performClick()

        assertEquals(listOf<String?>(HostSession.ID, appSession.id), openedSessionIds)
    }

    @Test
    fun `a plugin listed once in the rail keeps its plain name`() = runComposeUiTest {
        showRail(persistentListOf(row(sessionId = appSession.id)), onClickPlugin = {})

        onNodeWithContentDescription("Inspector").assertExists()
    }

    private fun ComposeUiTest.showRail(plugins: ImmutableList<DrawerPluginItemUiState>, onClickPlugin: (DrawerPluginItemUiState) -> Unit) {
        setContent {
            JwTheme(darkTheme = false) {
                ShrunkToolingDrawerView(
                    plugins = plugins,
                    sessions = persistentListOf(appSession),
                    selectedSession = appSession,
                    aiActivity = AiActivityUiState.Idle,
                    onFollowAiOperationChange = {},
                    onOpenMcpSettings = {},
                    onClickExpandMenu = {},
                    onClickSettings = {},
                    onClickPlugin = onClickPlugin,
                    onClickInfo = {},
                    onOpenAllMcpTools = {},
                    onSelectSession = {},
                )
            }
        }
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
