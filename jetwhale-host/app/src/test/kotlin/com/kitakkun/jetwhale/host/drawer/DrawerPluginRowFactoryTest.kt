package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.McpToolPermission
import com.kitakkun.jetwhale.host.model.McpToolSummary
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginMetaData
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawerPluginRowFactoryTest {
    private val app = DebugSession(
        id = "app-1",
        name = "Demo",
        isActive = true,
        transportSecurity = SessionTransportSecurity.LOOPBACK,
        installedPlugins = persistentListOf(JetWhalePluginInfo(PLUGIN_ID, "1.0.0")),
    )

    private val appWithoutPlugin = app.copy(installedPlugins = persistentListOf())

    private val bothSessionsPlugin = plugin(requiresAgent = true, targetsHostSession = true)

    @Test
    fun `a plugin that runs in the host session and in the selected app gets a row for each session`() {
        val rows = factory(selectedSession = app, selectedPluginSessionId = null).createRows(listOf(bothSessionsPlugin))

        assertEquals(listOf(HostSession.ID, app.id), rows.map(DrawerPluginItemUiState::sessionId))
        assertEquals(2, rows.map(DrawerPluginItemUiState::rowKey).toSet().size)
    }

    @Test
    fun `a plugin that also runs in apps keeps only its host row while the selected app lacks it`() {
        val rows = factory(selectedSession = appWithoutPlugin, selectedPluginSessionId = null).createRows(listOf(bothSessionsPlugin))

        assertEquals(listOf<String?>(HostSession.ID), rows.map(DrawerPluginItemUiState::sessionId))
    }

    @Test
    fun `a plugin that also runs in apps keeps its host row with no app selected`() {
        val rows = factory(selectedSession = null, selectedPluginSessionId = null).createRows(listOf(bothSessionsPlugin))

        assertEquals(listOf<String?>(HostSession.ID), rows.map(DrawerPluginItemUiState::sessionId))
    }

    @Test
    fun `an app plugin the selected app lacks stays listed as not in the app`() {
        val rows = factory(selectedSession = appWithoutPlugin, selectedPluginSessionId = null).createRows(listOf(plugin(requiresAgent = true, targetsHostSession = false)))

        assertEquals(listOf(app.id to PluginAvailability.Unavailable), rows.map { it.sessionId to it.pluginAvailability })
    }

    @Test
    fun `a host-only plugin has a single row in the host section`() {
        val rows = factory(selectedSession = app, selectedPluginSessionId = null).createRows(listOf(plugin(requiresAgent = false, targetsHostSession = true)))

        assertEquals(listOf<String?>(HostSession.ID), rows.map(DrawerPluginItemUiState::sessionId))
    }

    @Test
    fun `only the row of the session that was opened is selected`() {
        val rows = factory(selectedSession = app, selectedPluginSessionId = HostSession.ID).createRows(listOf(bothSessionsPlugin))

        assertEquals(listOf(HostSession.ID to true, app.id to false), rows.map { it.sessionId to it.isSelected })
    }

    @Test
    fun `each row shows the MCP tools and the AI activity of its own session`() {
        val rows = DrawerPluginRowFactory(
            selectedSession = app,
            enabledPluginIds = setOf(PLUGIN_ID),
            mcpCapablePlugins = McpCapablePlugins(mapOf(HostSession.ID to mapOf(PLUGIN_ID to listOf(McpToolSummary("tool", "", emptyList()))))),
            headlessPlugins = HeadlessPlugins.Empty,
            activeInvocation = McpToolInvocation(
                id = 1,
                toolName = "tool",
                permission = McpToolPermission.PluginTool("tool"),
                pluginId = PLUGIN_ID,
                sessionId = app.id,
                arguments = persistentListOf(),
            ),
            selectedPluginId = "",
            selectedPluginSessionId = null,
        ).createRows(listOf(bothSessionsPlugin))

        assertEquals(
            listOf(Triple(HostSession.ID, true, false), Triple(app.id, false, true)),
            rows.map { Triple(it.sessionId, it.exposesMcpTools, it.underAiControl) },
        )
    }

    private fun factory(selectedSession: DebugSession?, selectedPluginSessionId: String?) = DrawerPluginRowFactory(
        selectedSession = selectedSession,
        enabledPluginIds = setOf(PLUGIN_ID),
        mcpCapablePlugins = McpCapablePlugins.Empty,
        headlessPlugins = HeadlessPlugins.Empty,
        activeInvocation = null,
        selectedPluginId = PLUGIN_ID,
        selectedPluginSessionId = selectedPluginSessionId,
    )

    private fun plugin(requiresAgent: Boolean, targetsHostSession: Boolean) = PluginMetaData(
        name = "Inspector",
        id = PLUGIN_ID,
        version = "1.0.0",
        requiresAgent = requiresAgent,
        targetsHostSession = targetsHostSession,
    )

    private companion object {
        const val PLUGIN_ID = "com.example.inspector"
    }
}
