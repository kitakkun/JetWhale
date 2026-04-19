package com.kitakkun.jetwhale.host.mcp.tools

import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.protocol.negotiation.JetWhalePluginInfo
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionToolsTest {

    private val pluginFactoryRepository = mock<PluginFactoryRepository>()
    private val pluginInstanceService = mock<PluginInstanceService>()

    @Test
    fun `listSessions returns empty array when no sessions exist`() = runBlocking {
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf())
        }
        assertEquals("[]", listSessions(repo))
    }

    @Test
    fun `listSessions returns correct JSON for a session`() = runBlocking {
        val session = DebugSession(
            id = "session-id-123",
            name = "TestDevice",
            isActive = true,
            installedPlugins = persistentListOf(JetWhalePluginInfo("com.example.plugin", "1.0")),
        )
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session))
        }

        val expected = """[{"sessionId":"session-id-123","sessionName":"TestDevice","isActive":true,"installedPlugins":["com.example.plugin"]}]"""
        assertEquals(expected, listSessions(repo))
    }

    @Test
    fun `listSessions reflects isActive = false correctly`() = runBlocking {
        val session = DebugSession(
            id = "session-id-456",
            name = "InactiveDevice",
            isActive = false,
            installedPlugins = persistentListOf(),
        )
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session))
        }

        val expected = """[{"sessionId":"session-id-456","sessionName":"InactiveDevice","isActive":false,"installedPlugins":[]}]"""
        assertEquals(expected, listSessions(repo))
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
            installedPlugins = persistentListOf(JetWhalePluginInfo("com.example.plugin", "1.0")),
        )
        val repo = mock<DebugSessionRepository> {
            every { debugSessionsFlow } returns flowOf(persistentListOf(session))
        }
        every { pluginFactoryRepository.loadedPluginFactories } returns emptyMap()

        val result = listPlugins("session-abc", repo, pluginFactoryRepository, pluginInstanceService)
        assertEquals("[]", result)
    }
}
