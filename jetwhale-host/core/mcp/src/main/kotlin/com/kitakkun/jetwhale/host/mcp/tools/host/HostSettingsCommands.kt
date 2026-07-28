package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SERVER_RESTART_NOTE = "Every agent session was dropped; call jetwhale.listSessions again before using any sessionId you were holding."

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class UpdateSettingsCommand(
    private val settingsRepository: DebuggerSettingsRepository,
    private val debugWebSocketServer: DebugWebSocketServer,
) : HostMcpCommand() {
    override val name: String = "jetwhale.updateSettings"
    override val description: String =
        "Host-wide: changes the debug tool's settings. Only the arguments you supply are touched. Changing a ws/wss setting restarts the debug server, which drops every agent session — pass restartDebugServer=false to persist the change and apply it later instead."

    private val serverPort by intOrNull("Port the debug WebSocket server listens on.")
    private val wssPort by intOrNull("Port the secure (wss) connector listens on.")
    private val wssEnabled by booleanOrNull("Whether the secure (wss) connector is exposed at all.")
    private val mcpServerPort by intOrNull("Port this MCP server listens on. Persisted only — see the note in the result.")
    private val adbAutoPortMappingEnabled by booleanOrNull("Whether the host runs `adb reverse` automatically for connected Android devices.")
    private val checkForUpdatesOnStartup by booleanOrNull("Whether the host checks for a newer release when it starts.")
    private val persistData by booleanOrNull("Whether captured debug data survives a host restart.")
    private val restartDebugServer by booleanOrNull("Whether to restart the debug server so ws/wss changes take effect now. Defaults to true when a ws/wss setting changed.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        // Validate every port before writing any of them, so a bad argument late in the list cannot
        // leave the settings half-applied.
        val newServerPort = arguments[serverPort]?.requireValidPort("serverPort")
        val newWssPort = arguments[wssPort]?.requireValidPort("wssPort")
        val newMcpServerPort = arguments[mcpServerPort]?.requireValidPort("mcpServerPort")

        val applied = mutableMapOf<String, String>()
        val notes = mutableListOf<String>()

        newServerPort?.let { port ->
            settingsRepository.updateServerPort(port)
            applied["serverPort"] = port.toString()
        }
        newWssPort?.let { port ->
            settingsRepository.updateWssPort(port)
            applied["wssPort"] = port.toString()
        }
        arguments[wssEnabled]?.let { enabled ->
            settingsRepository.updateWssEnabled(enabled)
            applied["wssEnabled"] = enabled.toString()
        }
        newMcpServerPort?.let { port ->
            settingsRepository.updateMcpServerPort(port)
            applied["mcpServerPort"] = port.toString()
            // Restarting the MCP server here would tear down the very connection carrying this call.
            notes += "mcpServerPort was saved but this MCP server is still listening on its old port; the change takes effect the next time the host starts."
        }
        arguments[adbAutoPortMappingEnabled]?.let { enabled ->
            settingsRepository.updateAdbAutoPortMappingEnabled(enabled)
            applied["adbAutoPortMappingEnabled"] = enabled.toString()
        }
        arguments[checkForUpdatesOnStartup]?.let { enabled ->
            settingsRepository.updateCheckForUpdatesOnStartup(enabled)
            applied["checkForUpdatesOnStartup"] = enabled.toString()
        }
        arguments[persistData]?.let { enabled ->
            settingsRepository.updatePersistData(enabled)
            applied["persistData"] = enabled.toString()
        }

        if (applied.isEmpty()) {
            throw JetWhaleMcpArgumentException("no settings given: supply at least one setting to change")
        }

        val debugServerAffected = applied.keys.any { it in DEBUG_SERVER_SETTINGS }
        val shouldRestart = arguments[restartDebugServer] ?: debugServerAffected
        if (shouldRestart) {
            restartDebugServer(settingsRepository, debugWebSocketServer)
            notes += SERVER_RESTART_NOTE
        } else if (debugServerAffected) {
            // Both the ports and adbAutoPortMappingEnabled are only read when the server starts.
            notes += "The debug server is still running with its previous configuration; restart it with jetwhale.restartDebugServer to apply the change."
        }

        return Json.encodeToString(
            UpdateSettingsResult(
                applied = applied,
                debugServerRestarted = shouldRestart,
                mcpServerRestarted = false,
                notes = notes,
            ),
        )
    }

    private fun Int.requireValidPort(parameterName: String): Int {
        if (this !in 1..65535) throw JetWhaleMcpArgumentException("invalid $parameterName: expected 1..65535 but was $this")
        return this
    }

    private companion object {
        val DEBUG_SERVER_SETTINGS = setOf("serverPort", "wssPort", "wssEnabled", "adbAutoPortMappingEnabled")
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class RestartDebugServerCommand(
    private val settingsRepository: DebuggerSettingsRepository,
    private val debugWebSocketServer: DebugWebSocketServer,
) : HostMcpCommand() {
    override val name: String = "jetwhale.restartDebugServer"
    override val description: String =
        "Host-wide: stops and restarts the debug WebSocket server that agents connect to. This disconnects every session — every sessionId you hold becomes invalid and each app has to reconnect."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        restartDebugServer(settingsRepository, debugWebSocketServer)
        val status = debugWebSocketServer.statusFlow.value.toJson()
        return Json.encodeToString(
            RestartDebugServerResult(
                state = status.state,
                host = status.host,
                port = status.port,
                wssPort = status.wssPort,
                note = SERVER_RESTART_NOTE,
            ),
        )
    }
}

/** Mirrors the settings-screen restart path: stop, then start with whatever the wss settings now say. */
private suspend fun restartDebugServer(
    settingsRepository: DebuggerSettingsRepository,
    debugWebSocketServer: DebugWebSocketServer,
) = withContext(Dispatchers.IO) {
    debugWebSocketServer.stop()
    debugWebSocketServer.start(
        host = "localhost",
        port = settingsRepository.serverPortFlow.value,
        wssPort = settingsRepository.wssPortFlow.value.takeIf { settingsRepository.wssEnabledFlow.value },
    )
}

@Serializable
data class UpdateSettingsResult(
    val applied: Map<String, String>,
    val debugServerRestarted: Boolean,
    val mcpServerRestarted: Boolean,
    val notes: List<String>,
)

@Serializable
data class RestartDebugServerResult(
    val state: String,
    val host: String? = null,
    val port: Int? = null,
    val wssPort: Int? = null,
    val note: String,
)
