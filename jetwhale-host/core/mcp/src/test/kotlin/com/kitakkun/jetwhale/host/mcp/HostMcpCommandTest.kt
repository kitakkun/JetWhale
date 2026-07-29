package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpSse
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostMcpCommandTest {

    private val pluginInstanceService = mock<PluginInstanceService> {
        every { getLoadedPluginInstances() } returns emptyList()
        every { pluginInstanceEventFlow } returns MutableSharedFlow()
    }

    @Test
    fun `a host command's declared parameters appear in its input schema`() = withHostCommand(EchoHostCommand()) { client ->
        val properties = client.echoTool().inputSchema.properties
        assertEquals(setOf("text", "times"), properties?.keys)
        assertEquals("string", properties?.getValue("text")?.jsonObject?.getValue("type")?.jsonPrimitive?.content)
        assertEquals("Text to echo back.", properties?.getValue("text")?.jsonObject?.getValue("description")?.jsonPrimitive?.content)
        assertEquals(listOf("text"), client.echoTool().inputSchema.required)
    }

    @Test
    fun `a host command's schema carries no sessionId`() = withHostCommand(EchoHostCommand()) { client ->
        val tool = client.echoTool()
        assertFalse("sessionId" in tool.inputSchema.properties?.keys.orEmpty())
        assertFalse("sessionId" in tool.inputSchema.required.orEmpty())
    }

    @Test
    fun `a host command returns the string its execute produced`() = withHostCommand(EchoHostCommand()) { client ->
        val result = client.callTool("jetwhale.test.echo", mapOf("text" to "hi", "times" to 3))
        assertEquals("hihihi", result.firstText())
    }

    @Test
    fun `an invalid argument is returned as an error result instead of failing the tool call`() = withHostCommand(EchoHostCommand()) { client ->
        val result = client.callTool("jetwhale.test.echo", emptyMap())
        assertEquals(true, result.isError)
        assertContains(result.firstText(), "missing required argument: text")
    }

    @Test
    fun `an exception thrown by execute becomes an error result`() = withHostCommand(ExplodingHostCommand()) { client ->
        val result = client.callTool("jetwhale.test.explode", emptyMap())
        assertEquals(true, result.isError)
        assertContains(result.firstText(), "boom")
    }

    @Test
    fun `registering the same host command for a second connection does not reseal its parameters`() {
        val command = EchoHostCommand()
        withServer(command) { port ->
            repeat(2) {
                val client = connect(port)
                try {
                    assertTrue("text" in client.echoTool().inputSchema.properties?.keys.orEmpty())
                } finally {
                    client.close()
                }
            }
        }
    }

    private fun withHostCommand(command: HostMcpCommand, block: suspend (Client) -> Unit) = withServer(command) { port ->
        val client = connect(port)
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    private fun withServer(command: HostMcpCommand, block: suspend (port: Int) -> Unit) = runBlocking {
        val service = DefaultMcpServerService(
            pluginInstanceService = pluginInstanceService,
            mcpActivityRepository = FakeMcpActivityRepository(),
            builtInTools = setOf(command),
            mcpPermissionsRepository = FakeMcpPermissionsRepository(),
            statusHolder = McpServerStatusHolder(),
        )
        val port = java.net.ServerSocket(0).use { it.localPort }
        service.start("localhost", port)
        try {
            block(port)
        } finally {
            service.stop()
        }
    }

    private suspend fun connect(port: Int): Client = HttpClient(CIO) { install(SSE) }.mcpSse("http://localhost:$port/sse")
}

private suspend fun Client.echoTool(): Tool = listTools().tools.first { it.name.startsWith("jetwhale.test.") }

private fun CallToolResult.firstText(): String = content.filterIsInstance<TextContent>().first().text

private class EchoHostCommand : HostMcpCommand() {
    override val name: String = "jetwhale.test.echo"
    override val group: McpHostToolGroup = McpHostToolGroup.OBSERVE
    override val description: String = "Echoes text back."

    private val text by string("Text to echo back.")
    private val times by intOrNull("How many times to repeat it.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[text].repeat(arguments[times] ?: 1)
}

private class ExplodingHostCommand : HostMcpCommand() {
    override val name: String = "jetwhale.test.explode"
    override val group: McpHostToolGroup = McpHostToolGroup.OBSERVE
    override val description: String = "Always fails."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = throw IllegalStateException("boom")
}
