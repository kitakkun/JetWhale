package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.McpToolInvocation
import com.kitakkun.jetwhale.host.model.McpToolPermission
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpParameterDescriptor
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpToolDescriptor
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
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class FakeMcpTool(
    private val name: String,
    private val response: String = "ok",
    private val onExecute: () -> Unit = {},
) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = name,
            description = "Fake tool for testing",
            inputSchema = ToolSchema(),
            permission = McpToolPermission.Unrestricted,
        ) { _ ->
            onExecute()
            CallToolResult(content = listOf(TextContent(response)))
        }
    }
}

/** Returns a text block alongside a binary one, which history must name rather than inline. */
internal class MediaMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = name,
            description = "Returns text and an image",
            inputSchema = ToolSchema(),
            permission = McpToolPermission.Unrestricted,
        ) { _ ->
            CallToolResult(
                content = listOf(
                    TextContent("captured"),
                    ImageContent(data = "AAAA", mimeType = "image/png"),
                ),
            )
        }
    }
}

/** Reports a tool-level failure the way the protocol prefers: a normal return flagged `isError`. */
internal class ErrorResultMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = name,
            description = "Always reports an error result",
            inputSchema = ToolSchema(),
            permission = McpToolPermission.Unrestricted,
        ) { _ ->
            errorResult("no such element")
        }
    }
}

/** Answers with both prose and a machine-readable payload, as a tool with an output schema does. */
internal class StructuredMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = name,
            description = "Returns structured content",
            inputSchema = ToolSchema(),
            permission = McpToolPermission.Unrestricted,
        ) { _ ->
            CallToolResult(
                content = listOf(TextContent("measured")),
                structuredContent = buildJsonObject {
                    put("width", 120)
                    put("height", 40)
                },
            )
        }
    }
}

internal class FailingMcpTool(private val name: String) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = name,
            description = "Always throws",
            inputSchema = ToolSchema(),
            permission = McpToolPermission.Unrestricted,
        ) { _ ->
            error("boom")
        }
    }
}

internal class FakeMcpCapablePlugin(private val toolName: String = "com.example.test.greet") :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        object : JetWhaleMcpCommand() {
            override val name = toolName
            override val description = "Greet by name"

            private val greetName by string("Name to greet", name = "name")

            override suspend fun execute(arguments: JetWhaleMcpArguments): String = "Hello, ${arguments[greetName]}!"
        },
    )
}
