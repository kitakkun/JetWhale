package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DefaultMcpServerServicePluginToolsTest {

    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getLoadedPluginInstances() } returns emptyList()
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }

    private val service = DefaultMcpServerService(
        pluginInstanceService = pluginInstanceService,
        mcpActivityRepository = FakeMcpActivityRepository(),
        mcpPermissionsRepository = FakeMcpPermissionsRepository(),
        builtInTools = emptySet(),
        statusHolder = McpServerStatusHolder(),
    )

    private val host = "localhost"
    private val port = ServerSocket(0).use(ServerSocket::getLocalPort)

    @Test
    fun `plugin tools are registered when McpCapablePlugin instance exists at server start`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-abc123"
        val fakePlugin = FakeMcpCapablePlugin()

        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(testPluginId, testSessionId, fakePlugin, version = "1.0.0"),
        )
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val expectedToolName = "com.example.test.greet"

                val listResult = client.listTools()
                assertNotNull(listResult)
                val toolNames = listResult.tools.map(Tool::name)
                assertTrue(expectedToolName in toolNames, "Expected $expectedToolName in $toolNames")

                val callResult = client.callTool(
                    expectedToolName,
                    mapOf("sessionId" to testSessionId, "name" to "World"),
                )
                assertNotNull(callResult)
                val text = callResult.content.filterIsInstance<TextContent>().first().text
                assertEquals("Hello, World!", text)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @Test
    fun `plugin tools are registered via pluginInstanceEventFlow after server start`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 1)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow

        service.start(host, port)
        try {
            val testPluginId = "com.example.test"
            val testSessionId = "test-session-xyz789"
            val fakePlugin = FakeMcpCapablePlugin()
            every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(LoadedPluginInstance(testPluginId, testSessionId, fakePlugin, version = "1.0.0"))
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId, version = "1.0.0"))

            awaitCapableFor(testSessionId) { testPluginId in it }

            assertTrue("com.example.test.greet" in servedToolNames())
        } finally {
            service.stop()
        }
    }

    @Test
    fun `plugin tools are unregistered when Disposed event is received`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow

        service.start(host, port)
        try {
            val testPluginId = "com.example.test"
            val testSessionId = "test-session-def456"
            val fakePlugin = FakeMcpCapablePlugin()
            every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(LoadedPluginInstance(testPluginId, testSessionId, fakePlugin, version = "1.0.0"))
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId, version = "1.0.0"))

            awaitCapableFor(testSessionId) { testPluginId in it }
            assertTrue("com.example.test.greet" in servedToolNames())

            eventFlow.emit(PluginInstanceEvent.Disposed(testPluginId, testSessionId))

            awaitCapableFor(testSessionId) { testPluginId !in it }
            assertFalse("com.example.test.greet" in servedToolNames())
        } finally {
            service.stop()
        }
    }

    @Test
    fun `disposing app-session instances withdraws only those sessions' tools`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 4)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow

        service.start(host, port)
        try {
            val appPlugin = "com.example.app"
            val appSessions = listOf("app-session-1", "app-session-2")
            val hostPlugin = "com.example.host"
            val hostPluginInstance = LoadedPluginInstance(hostPlugin, HostSession.ID, FakeMcpCapablePlugin(toolName = "com.example.host.greet"), version = "1.0.0")
            every { pluginInstanceService.getLoadedPluginInstances() } returns appSessions.map { sessionId ->
                LoadedPluginInstance(appPlugin, sessionId, FakeMcpCapablePlugin(toolName = "com.example.app.greet"), version = "1.0.0")
            } + hostPluginInstance
            appSessions.forEach { eventFlow.emit(PluginInstanceEvent.Ready(appPlugin, it, version = "1.0.0")) }
            eventFlow.emit(PluginInstanceEvent.Ready(hostPlugin, HostSession.ID, version = "1.0.0"))
            awaitCapableFor(HostSession.ID) { hostPlugin in it }
            appSessions.forEach { sessionId -> awaitCapableFor(sessionId) { appPlugin in it } }

            every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(hostPluginInstance)
            appSessions.forEach { sessionId -> eventFlow.emit(PluginInstanceEvent.Disposed(appPlugin, sessionId)) }

            appSessions.forEach { sessionId -> awaitCapableFor(sessionId) { it.isEmpty() } }
            assertEquals(listOf("com.example.host.greet"), servedToolNames().filter { it.startsWith("com.example.") })
        } finally {
            service.stop()
        }
    }

    @Test
    fun `a Ready for an instance already replaced by another version registers nothing`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow

        service.start(host, port)
        try {
            val testPluginId = "com.example.test"
            val testSessionId = "test-session-replaced"
            val barrierPluginId = "com.example.other"
            every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
                LoadedPluginInstance(testPluginId, testSessionId, FakeMcpCapablePlugin(), version = "2.0.0"),
                LoadedPluginInstance(barrierPluginId, testSessionId, FakeMcpCapablePlugin(toolName = "com.example.other.greet"), version = "1.0.0"),
            )
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId, version = "1.0.0"))
            eventFlow.emit(PluginInstanceEvent.Ready(barrierPluginId, testSessionId, version = "1.0.0"))

            awaitCapableFor(testSessionId) { barrierPluginId in it }
            assertFalse(testPluginId in service.mcpCapablePluginsFlow.value.pluginIdsFor(testSessionId))
        } finally {
            service.stop()
        }
    }

    /**
     * Waits for the service to have handled a plugin lifecycle event: the registry publishes the
     * capable plugins as its last step in both registering and unregistering a plugin's tools, so
     * once the flow reports [condition] the tool list a new connection is served is settled too.
     */
    private suspend fun awaitCapableFor(sessionId: String, condition: (Set<String>) -> Boolean) {
        withTimeout(5.seconds) {
            service.mcpCapablePluginsFlow.first { condition(it.pluginIdsFor(sessionId)) }
        }
    }

    /** The tool list a freshly connected MCP client is served; it is computed at connection time. */
    private suspend fun servedToolNames(): List<String> {
        val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
        return try {
            client.listTools().tools.map(Tool::name)
        } finally {
            client.close()
        }
    }

    @Test
    fun `every MCP-capable plugin in a session is reported as capable`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow

        service.start(host, port)
        try {
            val sessionId = "test-session-multi"
            val pluginA = "com.example.a"
            val pluginB = "com.example.b"
            every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
                LoadedPluginInstance(pluginA, sessionId, FakeMcpCapablePlugin(toolName = "com.example.a.greet"), version = "1.0.0"),
                LoadedPluginInstance(pluginB, sessionId, FakeMcpCapablePlugin(toolName = "com.example.b.greet"), version = "1.0.0"),
            )
            eventFlow.emit(PluginInstanceEvent.Ready(pluginA, sessionId, version = "1.0.0"))
            eventFlow.emit(PluginInstanceEvent.Ready(pluginB, sessionId, version = "1.0.0"))

            val capable = withTimeout(5.seconds) {
                service.mcpCapablePluginsFlow.first { it.pluginIdsFor(sessionId).size == 2 }
            }
            assertEquals(setOf(pluginA, pluginB), capable.pluginIdsFor(sessionId))
        } finally {
            service.stop()
        }
    }
}
