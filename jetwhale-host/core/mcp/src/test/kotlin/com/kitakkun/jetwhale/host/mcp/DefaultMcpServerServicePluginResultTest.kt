package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultMcpServerServicePluginResultTest {

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
    fun `a plugin error result reaches the agent flagged as an error`() = runBlocking {
        val callResult = callPluginTool(FixedResultPlugin(JetWhaleMcpResult.error("no widget with id: 7")), sessionArguments)

        assertEquals(true, callResult.isError)
        assertEquals("no widget with id: 7", callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @Test
    fun `a failure a plugin throws reaches the agent flagged as an error`() = runBlocking {
        val callResult = callPluginTool(RejectingPlugin(), sessionArguments)

        assertEquals(true, callResult.isError)
        assertEquals("unknown widget id", callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @Test
    fun `a plugin structured result arrives as structuredContent repeated as text`() = runBlocking {
        val payload = buildJsonObject {
            put("width", 120)
            put("height", 40)
        }
        val callResult = callPluginTool(FixedResultPlugin(JetWhaleMcpResult.json(payload)), sessionArguments)

        assertEquals(false, callResult.isError)
        assertEquals(payload, callResult.structuredContent)
        assertEquals(payload.toString(), callResult.content.filterIsInstance<TextContent>().single().text)
    }

    @Test
    fun `a plugin image arrives Base64-encoded as an image block`() = runBlocking {
        val callResult = callPluginTool(FixedResultPlugin(JetWhaleMcpResult.image(data = byteArrayOf(1, 2, 3), mimeType = "image/png")), sessionArguments)

        val image = callResult.content.filterIsInstance<ImageContent>().single()
        assertEquals("AQID", image.data)
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun `a declared output schema reaches the agent on the listed tool`() = runBlocking {
        val tool = describePluginTool(DeclaredOutputPlugin(answersThroughOutput = true))

        val outputSchema = assertNotNull(tool.outputSchema, "Expected an output schema on $tool")
        assertEquals(listOf("widthPx", "heightPx", "label"), outputSchema.properties?.keys?.toList())
        assertEquals(listOf("widthPx", "heightPx"), outputSchema.required)
    }

    @Test
    fun `a command that declares no output advertises none`() = runBlocking {
        assertNull(describePluginTool(FakeMcpCapablePlugin(PLUGIN_TOOL)).outputSchema)
    }

    @Test
    fun `a declared output answers with structured content`() = runBlocking {
        val callResult = callPluginTool(DeclaredOutputPlugin(answersThroughOutput = true), sessionArguments)

        assertEquals(false, callResult.isError)
        assertEquals(
            buildJsonObject {
                put("widthPx", 120)
                put("heightPx", 40)
            },
            callResult.structuredContent,
        )
    }

    @Test
    fun `a plugin answer that bypasses its declared output reaches the agent as a failure`() = runBlocking {
        val callResult = callPluginTool(DeclaredOutputPlugin(answersThroughOutput = false), sessionArguments)

        assertEquals(true, callResult.isError)
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, "declares an output but answered without it")
    }

    @Test
    fun `a plugin tool called without a sessionId is told the sessions it runs in`() = runBlocking {
        val callResult = callPluginTool(FakeMcpCapablePlugin(PLUGIN_TOOL), emptyMap())

        assertEquals(true, callResult.isError)
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, "'sessionId' is required")
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, PLUGIN_SESSION)
    }

    @Test
    fun `a plugin tool called for a session that does not have it names that session`() = runBlocking {
        val callResult = callPluginTool(FakeMcpCapablePlugin(PLUGIN_TOOL), mapOf("sessionId" to "session-gone"))

        assertEquals(true, callResult.isError)
        assertContains(callResult.content.filterIsInstance<TextContent>().single().text, "session 'session-gone'")
    }

    private val sessionArguments = mapOf("sessionId" to PLUGIN_SESSION)

    /** Starts the service with [plugin] loaded in one session, and reads its tool off the tool list. */
    private suspend fun describePluginTool(plugin: JetWhaleHostPlugin): Tool {
        loadInOneSession(plugin)
        service.start(host, port)
        return try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.listTools().tools.single { it.name == PLUGIN_TOOL }
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    /** Starts the service with [plugin] loaded in one session, and calls its tool with [arguments]. */
    private suspend fun callPluginTool(plugin: JetWhaleHostPlugin, arguments: Map<String, String>): CallToolResult {
        loadInOneSession(plugin)
        service.start(host, port)
        return try {
            val client = HttpClient(CIO) { install(SSE) }.mcpSse("http://$host:$port/sse")
            try {
                client.callTool(PLUGIN_TOOL, arguments)
            } finally {
                client.close()
            }
        } finally {
            service.stop()
        }
    }

    private fun loadInOneSession(plugin: JetWhaleHostPlugin) {
        every { pluginInstanceService.getLoadedPluginInstances() } returns listOf(LoadedPluginInstance(PLUGIN_ID, PLUGIN_SESSION, plugin))
        every { pluginInstanceService.getPluginInstanceForSession(PLUGIN_ID, PLUGIN_SESSION) } returns plugin
    }
}

private const val PLUGIN_ID = "com.example.test"
private const val PLUGIN_SESSION = "test-session-result"
private const val PLUGIN_TOOL = "com.example.test.tool"

private class FixedResultPlugin(private val result: JetWhaleMcpResult) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = PLUGIN_TOOL
            override val description = "Answers with a fixed result"

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = result
        },
    )
}

private class RejectingPlugin :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpTextCommand() {
            override val name = PLUGIN_TOOL
            override val description = "Always rejects the call"

            override suspend fun executeText(arguments: JetWhaleMcpArguments): String = throw JetWhaleMcpArgumentException("unknown widget id")
        },
    )
}

@Serializable
private data class WidgetMeasurement(val widthPx: Int, val heightPx: Int, val label: String = "")

private class DeclaredOutputPlugin(private val answersThroughOutput: Boolean) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = PLUGIN_TOOL
            override val description = "Measures the selected widget"

            private val measurement = serializableOutput<WidgetMeasurement>()

            override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = when {
                answersThroughOutput -> measurement.result(WidgetMeasurement(widthPx = 120, heightPx = 40))
                else -> JetWhaleMcpResult.json(buildJsonObject { put("widthPx", "wide") })
            }
        },
    )
}
