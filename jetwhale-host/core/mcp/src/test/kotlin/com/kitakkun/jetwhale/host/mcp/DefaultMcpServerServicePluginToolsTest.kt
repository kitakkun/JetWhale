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
            LoadedPluginInstance(testPluginId, testSessionId, fakePlugin),
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
    fun `a plugin tool whose instance is gone answers that it is not available`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-gone"
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(testPluginId, testSessionId, FakeMcpCapablePlugin()),
        )
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns null

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val result = client.callTool(
                    "com.example.test.greet",
                    mapOf("sessionId" to testSessionId, "name" to "World"),
                )
                assertEquals(true, result.isError)
                val text = result.content.filterIsInstance<TextContent>().first().text
                assertTrue("is not available in session '$testSessionId'" in text, text)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @Test
    fun `a plugin tool called without a sessionId answers with the sessions it runs in`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-listed"
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(
            LoadedPluginInstance(testPluginId, testSessionId, FakeMcpCapablePlugin()),
        )

        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val result = client.callTool("com.example.test.greet", mapOf("name" to "World"))

                val text = result.content.filterIsInstance<TextContent>().first().text
                assertTrue("'sessionId' is required" in text, text)
                assertTrue(testSessionId in text, text)
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
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-xyz789"
        val fakePlugin = FakeMcpCapablePlugin()
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

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
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-def456"
        val fakePlugin = FakeMcpCapablePlugin()
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

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
        val appPlugin = "com.example.app"
        val appSessions = listOf("app-session-1", "app-session-2")
        appSessions.forEach { sessionId ->
            every { pluginInstanceService.getPluginInstanceForSession(appPlugin, sessionId) } returns FakeMcpCapablePlugin(toolName = "com.example.app.greet")
        }
        val hostPlugin = "com.example.host"
        every { pluginInstanceService.getPluginInstanceForSession(hostPlugin, HostSession.ID) } returns FakeMcpCapablePlugin(toolName = "com.example.host.greet")

        service.start(host, port)
        try {
            appSessions.forEach { eventFlow.emit(PluginInstanceEvent.Ready(appPlugin, it)) }
            eventFlow.emit(PluginInstanceEvent.Ready(hostPlugin, HostSession.ID))
            awaitCapableFor(HostSession.ID) { hostPlugin in it }
            appSessions.forEach { sessionId -> awaitCapableFor(sessionId) { appPlugin in it } }

            appSessions.forEach { sessionId ->
                every { pluginInstanceService.getPluginInstanceForSession(appPlugin, sessionId) } returns null
                eventFlow.emit(PluginInstanceEvent.Disposed(appPlugin, sessionId))
            }

            appSessions.forEach { sessionId -> awaitCapableFor(sessionId) { it.isEmpty() } }
            assertEquals(listOf("com.example.host.greet"), servedToolNames().filter { it.startsWith("com.example.") })
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
        val sessionId = "test-session-multi"
        val pluginA = "com.example.a"
        every { pluginInstanceService.getPluginInstanceForSession(pluginA, sessionId) } returns
            FakeMcpCapablePlugin(toolName = "com.example.a.greet")
        val pluginB = "com.example.b"
        every { pluginInstanceService.getPluginInstanceForSession(pluginB, sessionId) } returns
            FakeMcpCapablePlugin(toolName = "com.example.b.greet")

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(pluginA, sessionId))
            eventFlow.emit(PluginInstanceEvent.Ready(pluginB, sessionId))

            val capable = withTimeout(5.seconds) {
                service.mcpCapablePluginsFlow.first { it.pluginIdsFor(sessionId).size == 2 }
            }
            assertEquals(setOf(pluginA, pluginB), capable.pluginIdsFor(sessionId))
        } finally {
            service.stop()
        }
    }

    @Test
    fun `a plugin tool called after its plugin is disabled answers that it is not available`() = runBlocking {
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)
        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-disabled"
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns FakeMcpCapablePlugin()

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))
            awaitCapableFor(testSessionId) { testPluginId in it }
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                eventFlow.emit(PluginInstanceEvent.Disposed(testPluginId, testSessionId))
                awaitCapableFor(testSessionId) { testPluginId !in it }

                val result = client.callTool("com.example.test.greet", mapOf("sessionId" to testSessionId, "name" to "World"))

                assertEquals(true, result.isError)
                val text = (result.content.single() as TextContent).text
                assertTrue("is not available in session '$testSessionId'" in text, "Unexpected text: $text")
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }
}
