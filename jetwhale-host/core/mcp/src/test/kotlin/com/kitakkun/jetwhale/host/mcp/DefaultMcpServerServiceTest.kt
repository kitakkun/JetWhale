package com.kitakkun.jetwhale.host.mcp

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.host.mcp.tools.ClickMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.DragMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.GetAccessibilityTreeMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ListPluginsMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ListSessionsMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ScreenshotMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ScrollMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.TypeMcpTool
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawDebugOperationContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawHostPlugin
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultMcpServerServiceTest {

    private val debugSessionRepository = mock<DebugSessionRepository> {
        every { debugSessionsFlow } returns flowOf(persistentListOf())
    }
    private val pluginFactoryRepository = mock<PluginFactoryRepository>()
    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getLoadedPluginInstances() } returns emptyList()
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }
    private val pluginComposeSceneService = mock<PluginComposeSceneService>()
    private val builtInTools: Set<JetWhaleMcpTool> = setOf(
        ListSessionsMcpTool(debugSessionRepository),
        ListPluginsMcpTool(debugSessionRepository, pluginFactoryRepository, pluginInstanceService),
        ScreenshotMcpTool(pluginComposeSceneService),
        GetAccessibilityTreeMcpTool(pluginComposeSceneService),
        ClickMcpTool(pluginComposeSceneService),
        TypeMcpTool(pluginComposeSceneService),
        ScrollMcpTool(pluginComposeSceneService),
        DragMcpTool(pluginComposeSceneService),
    )

    private val service = DefaultMcpServerService(
        pluginInstanceService = pluginInstanceService,
        builtInTools = builtInTools,
    )

    private val host = "localhost"
    private val port = java.net.ServerSocket(0).use { it.localPort }

    @Test
    fun `listTools returns all expected built-in tools`() = runBlocking {
        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val result = client.listTools()
                assertNotNull(result)
                val toolNames = result.tools.map { it.name }
                assertTrue("jetwhale.listSessions" in toolNames)
                assertTrue("jetwhale.listPlugins" in toolNames)
                assertTrue("jetwhale.screenshot" in toolNames)
                assertTrue("jetwhale.getAccessibilityTree" in toolNames)
                assertTrue("jetwhale.click" in toolNames)
                assertTrue("jetwhale.type" in toolNames)
                assertTrue("jetwhale.scroll" in toolNames)
                assertTrue("jetwhale.drag" in toolNames)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @Test
    fun `listSessions returns empty array when no sessions are registered`() = runBlocking {
        service.start(host, port)
        try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val result = client.callTool("jetwhale.listSessions", emptyMap())
                assertNotNull(result)
                val textContent = result.content.filterIsInstance<TextContent>()
                assertEquals(1, textContent.size)
                assertEquals("[]", textContent.first().text)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @Test
    fun `start and stop can be called multiple times safely`() = runBlocking {
        service.start(host, port)
        service.start(host, port) // second call should be no-op
        service.stop()
        service.stop() // second call should be no-op
    }

    @OptIn(ExperimentalJetWhaleApi::class)
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
                val toolNames = listResult.tools.map { it.name }
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

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `plugin tools are registered via pluginInstanceEventFlow after server start`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-xyz789"
        val fakePlugin = FakeMcpCapablePlugin()
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 1)

        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            // Emit Ready event after server started (simulates Android device connecting later)
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

            // Connect a new client after the event was processed to pick up the newly registered tool
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                val listResult = client.listTools()
                assertNotNull(listResult)
                val toolNames = listResult.tools.map { it.name }
                assertTrue("com.example.test.greet" in toolNames, "Expected tool in $toolNames")
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `plugin tools are unregistered when Disposed event is received`() = runBlocking {
        val testPluginId = "com.example.test"
        val testSessionId = "test-session-def456"
        val fakePlugin = FakeMcpCapablePlugin()
        val eventFlow = MutableSharedFlow<PluginInstanceEvent>(extraBufferCapacity = 2)

        every { pluginInstanceService.pluginInstanceEventFlow } returns eventFlow
        every { pluginInstanceService.getPluginInstanceForSession(testPluginId, testSessionId) } returns fakePlugin

        service.start(host, port)
        try {
            eventFlow.emit(PluginInstanceEvent.Ready(testPluginId, testSessionId))

            // Verify tool is registered
            val clientBefore = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                assertTrue("com.example.test.greet" in clientBefore.listTools().tools.map { it.name })
            } finally {
                clientBefore.close()
            }

            // Emit Disposed event
            eventFlow.emit(PluginInstanceEvent.Disposed(testPluginId, testSessionId))

            // Reconnect and verify tool is gone
            val clientAfter = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                assertFalse("com.example.test.greet" in clientAfter.listTools().tools.map { it.name })
            } finally {
                clientAfter.close()
            }
        } finally {
            service.stop()
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private class FakeMcpCapablePlugin :
    JetWhaleRawHostPlugin(),
    JetWhaleMcpCapablePlugin {
    override fun onRawEvent(event: String) = Unit

    @Composable
    override fun ContentRaw(context: JetWhaleRawDebugOperationContext) = Unit

    override fun mcpTools() = listOf(
        JetWhaleMcpToolDescriptor(
            name = "com.example.test.greet",
            description = "Greet by name",
            parameters = mapOf(
                "name" to JetWhaleMcpParameterDescriptor(
                    type = "string",
                    description = "Name to greet",
                ),
            ),
        ),
    )

    override suspend fun handleMcpTool(toolName: String, arguments: Map<String, String>): String? = when (toolName) {
        "com.example.test.greet" -> "Hello, ${arguments["name"]}!"
        else -> null
    }
}
