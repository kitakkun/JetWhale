package com.kitakkun.jetwhale.host.drawer

import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
        assertEquals(HostSession.ID, uiState(selectedSessionId = app.id, row(DEVICE_PLUGIN_ID, HostSession.ID)).defaultPluginRow(DEVICE_PLUGIN_ID)?.sessionId)
        assertEquals(HostSession.ID, uiState(selectedSessionId = "", row(DEVICE_PLUGIN_ID, HostSession.ID)).defaultPluginRow(DEVICE_PLUGIN_ID)?.sessionId)
    }

    @Test
    fun `an app plugin opens in the selected app and nowhere with no app selected`() {
        assertEquals(app.id, uiState(selectedSessionId = app.id, row(NETWORK_PLUGIN_ID, app.id)).defaultPluginRow(NETWORK_PLUGIN_ID)?.sessionId)
        assertNull(uiState(selectedSessionId = "", row(NETWORK_PLUGIN_ID, null)).defaultPluginRow(NETWORK_PLUGIN_ID)?.sessionId)
    }

    @Test
    fun `a plugin with a row in each section opens in the selected app`() {
        val uiState = uiState(selectedSessionId = app.id, row(INSPECTOR_PLUGIN_ID, HostSession.ID), row(INSPECTOR_PLUGIN_ID, app.id))

        assertEquals(app.id, uiState.defaultPluginRow(INSPECTOR_PLUGIN_ID)?.sessionId)
    }

    @Test
    fun `a plugin that also runs in apps opens in the host session while the selected app lacks it`() {
        assertEquals(HostSession.ID, uiState(selectedSessionId = app.id, row(INSPECTOR_PLUGIN_ID, HostSession.ID)).defaultPluginRow(INSPECTOR_PLUGIN_ID)?.sessionId)
    }

    @Test
    fun `a plugin the drawer does not list has no row to open`() {
        assertNull(uiState(selectedSessionId = app.id, row(DEVICE_PLUGIN_ID, HostSession.ID)).defaultPluginRow(NETWORK_PLUGIN_ID))
    }

    private fun uiState(selectedSessionId: String, vararg rows: DrawerPluginItemUiState) = ToolingScaffoldUiState(
        selectedSessionId = selectedSessionId,
        selectedPluginId = "",
        sessions = persistentListOf(app),
        plugins = rows.toList().toImmutableList(),
        hasFailedJars = false,
        aiActivity = AiActivityUiState.Idle,
        sidebarWidth = 280.dp,
    )

    private fun row(id: String, sessionId: String?) = DrawerPluginItemUiState(
        name = id,
        id = id,
        sessionId = sessionId,
        activeIconResource = null,
        inactiveIconResource = null,
        pluginAvailability = PluginAvailability.Enabled,
        isSelected = false,
        underAiControl = false,
        exposesMcpTools = false,
        isHeadless = false,
    )

    private companion object {
        const val DEVICE_PLUGIN_ID = "com.example.device"
        const val NETWORK_PLUGIN_ID = "com.example.network"
        const val INSPECTOR_PLUGIN_ID = "com.example.inspector"
    }
}
