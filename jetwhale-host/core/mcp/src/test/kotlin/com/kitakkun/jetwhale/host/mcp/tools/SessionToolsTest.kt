package com.kitakkun.jetwhale.host.mcp.tools

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionToolsTest {

    private val pluginFactoryRepository = mock<PluginFactoryRepository>()
    private val pluginInstanceService = mock<PluginInstanceService>()

    private val hostOnlyPlugins = mapOf(
        "com.example.device" to loadedPlugin("com.example.device", requiresAgent = false, runsInHostSession = false),
        "com.example.plugin" to loadedPlugin("com.example.plugin", requiresAgent = true, runsInHostSession = false),
    )

    private val bothSessionsPlugin = "com.example.inspector" to loadedPlugin("com.example.inspector", requiresAgent = true, runsInHostSession = true)

    @Test
    fun `listSessions lists the host session with no app connected`() = runBlocking {
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf())
        }
        every { pluginFactoryRepository.loadedPlugins } returns hostOnlyPlugins

        val expected = """[{"sessionId":"host","sessionName":"Host","isActive":true,"installedPlugins":["com.example.device"]}]"""
        assertEquals(expected, listSessions(repo, pluginFactoryRepository))
    }

    @Test
    fun `listSessions returns correct JSON for a session`() = runBlocking {
        val session = DebugSession(
            id = "session-id-123",
            name = "TestDevice",
            isActive = true,
            transportSecurity = SessionTransportSecurity.PLAINTEXT,
            installedPlugins = persistentListOf(JetWhalePluginInfo("com.example.plugin", "1.0")),
        )
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session))
        }

        every { pluginFactoryRepository.loadedPlugins } returns emptyMap()

        val expected = """[$EMPTY_HOST_SESSION,{"sessionId":"session-id-123","sessionName":"TestDevice","isActive":true,"installedPlugins":["com.example.plugin"]}]"""
        assertEquals(expected, listSessions(repo, pluginFactoryRepository))
    }

    @Test
    fun `listSessions reflects isActive = false correctly`() = runBlocking {
        val session = DebugSession(
            id = "session-id-456",
            name = "InactiveDevice",
            isActive = false,
            transportSecurity = SessionTransportSecurity.PLAINTEXT,
            installedPlugins = persistentListOf(),
        )
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session))
        }

        every { pluginFactoryRepository.loadedPlugins } returns emptyMap()

        val expected = """[$EMPTY_HOST_SESSION,{"sessionId":"session-id-456","sessionName":"InactiveDevice","isActive":false,"installedPlugins":[]}]"""
        assertEquals(expected, listSessions(repo, pluginFactoryRepository))
    }

    @Test
    fun `listPlugins returns empty array when session is not found`() = runBlocking {
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf())
        }

        val result = listPlugins("unknown-id", repo, pluginFactoryRepository, pluginInstanceService)
        assertEquals("[]", result)
    }

    @Test
    fun `listPlugins returns empty array when session has no matching factories`() = runBlocking {
        val session = DebugSession(
            id = "session-abc",
            name = "Device",
            isActive = true,
            transportSecurity = SessionTransportSecurity.PLAINTEXT,
            installedPlugins = persistentListOf(JetWhalePluginInfo("com.example.plugin", "1.0")),
        )
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session))
        }
        every { pluginFactoryRepository.loadedPlugins } returns emptyMap()

        val result = listPlugins("session-abc", repo, pluginFactoryRepository, pluginInstanceService)
        assertEquals("[]", result)
    }

    @Test
    fun `listPlugins for the host session lists the plugins that need no app`() = runBlocking {
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf())
        }
        every { pluginFactoryRepository.loadedPlugins } returns hostOnlyPlugins
        every { pluginInstanceService.getPluginInstanceForSession("com.example.device", HostSession.ID) } returns null

        val result = listPlugins(HostSession.ID, repo, pluginFactoryRepository, pluginInstanceService)
        assertEquals("""[{"pluginId":"com.example.device","pluginName":"com.example.device","version":"1.0.0","mcpCapable":false}]""", result)
    }

    @Test
    fun `listSessions lists a plugin that also runs in apps under the host session`() = runBlocking {
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf())
        }
        every { pluginFactoryRepository.loadedPlugins } returns hostOnlyPlugins + bothSessionsPlugin

        val expected = """[{"sessionId":"host","sessionName":"Host","isActive":true,"installedPlugins":["com.example.device","com.example.inspector"]}]"""
        assertEquals(expected, listSessions(repo, pluginFactoryRepository))
    }

    @Test
    fun `listPlugins for the host session lists a plugin that also runs in apps, by its host instance`() = runBlocking {
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf())
        }
        every { pluginFactoryRepository.loadedPlugins } returns mapOf(bothSessionsPlugin)
        every { pluginInstanceService.getPluginInstanceForSession("com.example.inspector", HostSession.ID) } returns McpCapableHostPlugin()

        val result = listPlugins(HostSession.ID, repo, pluginFactoryRepository, pluginInstanceService)
        assertEquals("""[{"pluginId":"com.example.inspector","pluginName":"com.example.inspector","version":"1.0.0","mcpCapable":true}]""", result)
    }

    private class McpCapableHostPlugin :
        JetWhaleHostPlugin(),
        JetWhaleMcpCapablePlugin {
        override val mcpCommands: List<JetWhaleMcpCommand> = emptyList()
    }
}

private const val EMPTY_HOST_SESSION = """{"sessionId":"host","sessionName":"Host","isActive":true,"installedPlugins":[]}"""

private fun loadedPlugin(pluginId: String, requiresAgent: Boolean, runsInHostSession: Boolean) = LoadedHostPlugin(
    manifest = JetWhaleHostPluginManifest(
        pluginId = pluginId,
        pluginName = pluginId,
        version = "1.0.0",
        factoryClass = "$pluginId.Factory",
        requiresAgent = requiresAgent,
        runsInHostSession = runsInHostSession,
    ),
    factory = object : JetWhaleHostPluginFactory {
        override fun createPlugin(): JetWhaleHostPlugin = throw UnsupportedOperationException()
    },
)
