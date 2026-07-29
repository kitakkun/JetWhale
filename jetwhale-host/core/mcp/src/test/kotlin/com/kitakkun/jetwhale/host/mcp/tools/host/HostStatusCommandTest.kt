package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.McpServerStatusHolder
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.HostViewState
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostStatusCommandTest {

    private val currentView = MutableStateFlow<HostViewState?>(null)
    private val mcpServerStatusHolder = McpServerStatusHolder().apply {
        update(McpServerStatus.Running(host = "localhost", port = 7080))
    }

    private val command = HostStatusCommand(
        hostVersionInfo = HostVersionInfo("1.2.3-SNAPSHOT"),
        debugWebSocketServer = mock<DebugWebSocketServer> {
            every { statusFlow } returns MutableStateFlow(DebugWebSocketServerStatus.Started("localhost", 5080, 5443))
        },
        mcpServerStatusHolder = mcpServerStatusHolder,
        debugSessionRepository = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session("s1", isActive = true), session("s2", isActive = false)))
        },
        pluginFactoryRepository = mock<PluginFactoryRepository> {
            every { loadedPlugins } returns emptyMap()
            every { failedJarsFlow } returns MutableStateFlow(emptyList())
        },
        enabledPluginsRepository = mock<EnabledPluginsRepository> {
            every { enabledPluginIdsFlow } returns MutableStateFlow(emptySet())
        },
        pluginTrustService = mock<PluginTrustService> {
            every { untrustedJarPathsFlow } returns MutableStateFlow(emptyList())
        },
        pluginInstallProgressRepository = mock<PluginInstallProgressRepository> {
            every { progressFlow } returns MutableStateFlow(null)
        },
        settingsRepository = mock<DebuggerSettingsRepository> {
            every { serverPortFlow } returns MutableStateFlow(5080)
            every { wssPortFlow } returns MutableStateFlow(5443)
            every { wssEnabledFlow } returns MutableStateFlow(true)
            every { mcpServerPortFlow } returns MutableStateFlow(7080)
            every { adbAutoPortMappingEnabledFlow } returns MutableStateFlow(true)
            every { checkForUpdatesOnStartupFlow } returns MutableStateFlow(true)
            every { persistDataFlow } returns MutableStateFlow(false)
            every { mcpPluginInstallAllowedFlow } returns MutableStateFlow(false)
        },
        hostNavigationService = mock<HostNavigationService> {
            every { this@mock.currentView } returns this@HostStatusCommandTest.currentView
        },
    )

    @Test
    fun `getStatus reports the debug server and mcp server endpoints`() = runBlocking {
        val status = command.executeForText(arguments()).decode()

        assertEquals("Started", status.debugServer.state)
        assertEquals(5080, status.debugServer.port)
        assertEquals(5443, status.debugServer.wssPort)
        assertEquals("Running", status.mcpServer.state)
        assertEquals(7080, status.mcpServer.port)
    }

    @Test
    fun `getStatus reports the host version and whether it is a snapshot`() = runBlocking {
        val status = command.executeForText(arguments()).decode()

        assertEquals("1.2.3-SNAPSHOT", status.host.version)
        assertTrue(status.host.isSnapshot)
    }

    @Test
    fun `getStatus counts sessions by whether they are still connected`() = runBlocking {
        val status = command.executeForText(arguments()).decode()

        assertEquals(2, status.sessions.total)
        assertEquals(1, status.sessions.active)
    }

    @Test
    fun `getStatus reports a null ui block before the host window has composed`() = runBlocking {
        assertNull(command.executeForText(arguments()).decode().ui)
    }

    @Test
    fun `getStatus reports what the host window shows once it has composed`() = runBlocking {
        currentView.value = HostViewState(
            destination = HostDestination(kind = HostDestinationKind.PLUGIN, pluginId = "com.example", sessionId = "s1"),
            selectedSessionId = "s1",
            selectedPluginId = "com.example",
        )

        val ui = requireNotNull(command.executeForText(arguments()).decode().ui)
        assertEquals("PLUGIN", ui.destination)
        assertEquals("com.example", ui.pluginId)
        assertEquals("s1", ui.selectedSessionId)
    }

    @Test
    fun `getStatus reports that no install is in flight`() = runBlocking {
        assertFalse(command.executeForText(arguments()).decode().plugins.installInProgress)
    }

    @Test
    fun `getStatus reports whether installing plugins over MCP is allowed`() = runBlocking {
        // Without this an agent could only discover the gate by attempting an install and being refused.
        assertFalse(command.executeForText(arguments()).decode().settings.mcpPluginInstallAllowed)
    }
}

private fun session(id: String, isActive: Boolean) = DebugSession(
    id = id,
    name = id,
    isActive = isActive,
    transportSecurity = SessionTransportSecurity.LOOPBACK,
    installedPlugins = persistentListOf(),
)

private fun arguments() = JetWhaleMcpArguments(JsonObject(emptyMap()))

private fun String.decode() = Json.decodeFromString<HostStatusResult>(this)
