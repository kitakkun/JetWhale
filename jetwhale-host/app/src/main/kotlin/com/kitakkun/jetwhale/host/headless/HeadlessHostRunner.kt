package com.kitakkun.jetwhale.host.headless

import com.kitakkun.jetwhale.host.ApplicationLifecycleOwner
import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Exit code reported when a listener could not be brought up. */
private const val EXIT_CODE_STARTUP_FAILED = 1

/** How long to wait for both listeners to report a bound port before giving up on the run. */
private const val STARTUP_TIMEOUT_MILLIS = 60_000L

/** How long a signalled process may spend releasing its ports and adb port mappings. */
private const val SHUTDOWN_TIMEOUT_MILLIS = 10_000L

/** Prefix every readiness line carries, so a CI script can wait on it without parsing log output. */
private const val READINESS_PREFIX = "JetWhale headless:"

/**
 * Runs the host with no window: the agent WebSocket server, the MCP server, plugin instances and
 * adb auto-wiring, but no composition.
 *
 * The servers themselves are started by [ApplicationLifecycleOwner] and need no window. This adds
 * back only what the composition would otherwise be responsible for in a windowed run:
 * - disposing plugin compose scenes as sessions and plugins go away (`JetWhaleApp` does this from
 *   the composition, which never runs here), and
 * - keeping the process alive until it is asked to stop.
 *
 * It also reports the ports it actually bound, and fails the run if it could not bind them: a
 * second host on the same ports leaves the first one serving and the second one silently useless,
 * which is invisible without a window to look at.
 */
@Inject
@SingleIn(AppScope::class)
class HeadlessHostRunner(
    private val applicationLifecycleOwner: ApplicationLifecycleOwner,
    private val debugWebSocketServer: DebugWebSocketServer,
    private val mcpServerService: McpServerService,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val pluginComposeSceneService: PluginComposeSceneService,
) {
    /**
     * Suspends until the host is asked to shut down, and returns the process exit code.
     *
     * [ApplicationLifecycleOwner.initialize] must already have been called: this waits for the
     * listeners it starts.
     */
    suspend fun run(): Int = coroutineScope {
        installShutdownHook()

        val sceneHousekeepingJob = launch { disposeScenesForDepartedSessions() }

        val startup = withTimeoutOrNull(STARTUP_TIMEOUT_MILLIS) { awaitListeners() }
            ?: ListenerStartup.Failed("A listener did not report a bound port within $STARTUP_TIMEOUT_MILLIS ms.")

        if (startup is ListenerStartup.Failed) {
            System.err.println("$READINESS_PREFIX startup failed — ${startup.reason}")
            sceneHousekeepingJob.cancel()
            shutdownAndAwait()
            return@coroutineScope EXIT_CODE_STARTUP_FAILED
        }

        println("$READINESS_PREFIX ready")

        applicationLifecycleOwner.applicationStateFlow.first { it == ApplicationLifecycleOwner.ApplicationState.STOPPED }
        sceneHousekeepingJob.cancel()
        0
    }

    private sealed interface ListenerStartup {
        data object Ready : ListenerStartup
        data class Failed(val reason: String) : ListenerStartup
    }

    /** Waits for both listeners, reporting the ports they bound or why they could not bind them. */
    private suspend fun awaitListeners(): ListenerStartup {
        val webSocketStatus = debugWebSocketServer.statusFlow
            .first { it is DebugWebSocketServerStatus.Started || it is DebugWebSocketServerStatus.Error }
        if (webSocketStatus is DebugWebSocketServerStatus.Error) {
            return ListenerStartup.Failed("agent WebSocket server: ${webSocketStatus.message}")
        }
        val started = webSocketStatus as DebugWebSocketServerStatus.Started
        println("$READINESS_PREFIX agent ws://${started.host}:${started.port}" + (started.wssPort?.let { " wss port $it" } ?: ""))

        val mcpStatus = mcpServerService.statusFlow
            .first { it is McpServerStatus.Running || it is McpServerStatus.Error }
        if (mcpStatus is McpServerStatus.Error) {
            return ListenerStartup.Failed("MCP server: ${mcpStatus.message}")
        }
        val running = mcpStatus as McpServerStatus.Running
        println("$READINESS_PREFIX mcp http://${running.host}:${running.port}/sse")

        return ListenerStartup.Ready
    }

    /**
     * Closes plugin compose scenes whose session, server or plugin has gone away.
     *
     * Scenes are created on demand by the MCP tools even with no window, so without this a headless
     * run keeps composing plugin code for sessions that have already disconnected.
     */
    private suspend fun disposeScenesForDepartedSessions(): Unit = coroutineScope {
        launch {
            debugWebSocketServer.sessionClosedFlow.collect { sessionId ->
                pluginComposeSceneService.disposePluginSceneForSession(sessionId)
            }
        }
        launch {
            debugWebSocketServer.serverStoppedFlow.collect {
                pluginComposeSceneService.disposeAllPluginScenes()
            }
        }
        launch {
            enabledPluginsRepository.disabledPluginIdFlow.collect { pluginId ->
                pluginComposeSceneService.disposePluginScenesForPlugin(pluginId)
            }
        }
    }

    /**
     * Makes SIGINT/SIGTERM a graceful stop. Without it the JVM dies with its ports still bound from
     * the OS's point of view and its adb port mappings still installed on attached devices.
     */
    private fun installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                applicationLifecycleOwner.shutdown()
                runBlocking { awaitStopped() }
            },
        )
    }

    private suspend fun shutdownAndAwait() {
        applicationLifecycleOwner.shutdown()
        awaitStopped()
    }

    private suspend fun awaitStopped() {
        withTimeoutOrNull(SHUTDOWN_TIMEOUT_MILLIS) {
            applicationLifecycleOwner.applicationStateFlow.first { it == ApplicationLifecycleOwner.ApplicationState.STOPPED }
        }
    }
}
