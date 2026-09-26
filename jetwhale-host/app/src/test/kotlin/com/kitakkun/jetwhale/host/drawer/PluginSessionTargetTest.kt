package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginSessionTargetTest {
    private val app = DebugSession(
        id = "app-1",
        name = "Demo",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(),
    )

    @Test
    fun `a tool opens in the host session whatever app is selected`() {
        assertEquals(HostSession.ID, uiState(selectedSessionId = app.id).sessionIdFor("com.example.device"))
        assertEquals(HostSession.ID, uiState(selectedSessionId = "").sessionIdFor("com.example.device"))
    }

    @Test
    fun `an app plugin opens in the selected app and nowhere with no app selected`() {
        assertEquals(app.id, uiState(selectedSessionId = app.id).sessionIdFor("com.example.network"))
        assertNull(uiState(selectedSessionId = "").sessionIdFor("com.example.network"))
    }

    private fun uiState(selectedSessionId: String) = ToolingScaffoldUiState(
        selectedSessionId = selectedSessionId,
        selectedPluginId = "",
        sessions = persistentListOf(app),
        plugins = persistentListOf(item("com.example.device", needsApp = false), item("com.example.network", needsApp = true)),
        hasFailedJars = false,
        aiActivity = AiActivityUiState.Idle,
        sidebarWidth = 280.dp,
    )

    private fun item(id: String, needsApp: Boolean) = DrawerPluginItemUiState(
        name = id,
        id = id,
        activeIconResource = null,
        inactiveIconResource = null,
        pluginAvailability = PluginAvailability.Enabled,
        underAiControl = false,
        exposesMcpTools = false,
        isHeadless = false,
        needsApp = needsApp,
    )
}
