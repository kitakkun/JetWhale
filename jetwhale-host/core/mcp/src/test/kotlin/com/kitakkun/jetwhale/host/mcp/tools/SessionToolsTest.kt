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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

        val result = Json.parseToJsonElement(listSessions(repo)).jsonArray
        assertEquals(1, result.size)
        val obj = result[0].jsonObject
        assertEquals("session-id-123", obj["sessionId"]?.jsonPrimitive?.content)
        assertEquals("TestDevice", obj["sessionName"]?.jsonPrimitive?.content)
        assertEquals("true", obj["isActive"]?.jsonPrimitive?.content)
        val plugins = obj["installedPlugins"]?.jsonArray
        assertEquals(1, plugins?.size)
        assertEquals("com.example.plugin", plugins?.get(0)?.jsonPrimitive?.content)
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
        assertTrue(Json.parseToJsonElement(result).jsonArray.isEmpty())
    }
}
