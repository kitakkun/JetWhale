package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.mcp.tools.ClickMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.DragMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.GetAccessibilityTreeMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ListPluginsMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ListSessionsMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ScreenshotMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.ScrollMcpTool
import com.kitakkun.jetwhale.host.mcp.tools.TypeMcpTool
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultMcpServerServiceTest {

    private val debugSessionRepository = mock<DebugSessionRepository> {
        every { debugSessionsFlow } returns flowOf(persistentListOf())
    }
    private val pluginFactoryRepository = mock<PluginFactoryRepository>()
    private val pluginInstanceService = mock<PluginInstanceService>()
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
    private val port = 19876

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
}
