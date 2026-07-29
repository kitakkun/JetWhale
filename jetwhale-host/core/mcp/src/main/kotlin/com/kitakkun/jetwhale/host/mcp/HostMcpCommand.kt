package com.kitakkun.jetwhale.host.mcp

import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * A built-in MCP tool scoped to the debug tool itself, written with the same parameter DSL that
 * plugins use for their own commands.
 *
 * Unlike a plugin command — which [McpToolRegistry] routes to one plugin instance and which
 * therefore carries an injected `sessionId` — a host command targets the host as a whole, so its
 * schema is exactly the parameters it declares.
 *
 * Implementations must be stateless beyond their injected dependencies: one instance serves every
 * SSE connection, concurrently.
 *
 * Contribute one with an explicit binding, because the single declared supertype of a subclass is
 * [HostMcpCommand], not [JetWhaleMcpTool]:
 * ```kotlin
 * @Inject
 * @ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
 * class MyHostCommand(...) : HostMcpCommand()
 * ```
 */
abstract class HostMcpCommand :
    JetWhaleMcpTextCommand(),
    JetWhaleMcpTool {

    // Lazy, never an eager property: base-class initializers run before the subclass declares its
    // parameters, and reading the descriptor seals the parameter list. Producing it is idempotent,
    // so caching it here only avoids rebuilding it once per SSE connection.
    private val descriptor by lazy { toDescriptor() }

    final override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = descriptor.name,
            description = descriptor.description,
            inputSchema = descriptor.toToolSchema(),
        ) { request ->
            try {
                execute(JetWhaleMcpArguments(JsonObject(request.arguments ?: emptyMap()))).toCallToolResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: JetWhaleMcpException) {
                errorResult(e.message.orEmpty())
            } catch (e: Exception) {
                // Host tools do real I/O — downloads, socket binds, plugin loading. A failure has to
                // reach the agent as a readable result rather than tearing down the MCP connection.
                errorResult("${e::class.simpleName}: ${e.message.orEmpty()}")
            }
        }
    }
}
