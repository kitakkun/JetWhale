package com.kitakkun.jetwhale.host.cli

import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.ServerPortOverrides

/**
 * @property logLevel Null when `--log-level` was not passed, which leaves whatever `logback.xml`
 * configures in place. Applying a default here would quietly reduce what the host logs — and so
 * what the log viewer can show — for every launch that never asked for it.
 * @property headless Runs the servers with no window, for CI and agent-driven QA. See
 * [com.kitakkun.jetwhale.host.headless.HeadlessHostRunner].
 */
data class JetWhaleCliOptions(
    val pluginDirs: List<String>,
    val logLevel: JetWhaleLogLevel?,
    val serverPortOverrides: ServerPortOverrides,
    val mcpPermissionOverride: McpPermissionOverride,
    val headless: Boolean,
)

enum class JetWhaleLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

class CommandLineArgumentsParser {
    fun parse(args: Array<String>): JetWhaleCliOptions {
        var options = JetWhaleCliOptions(
            pluginDirs = emptyList(),
            logLevel = null,
            serverPortOverrides = ServerPortOverrides(serverPort = null, wssPort = null, mcpServerPort = null),
            mcpPermissionOverride = McpPermissionOverride.None,
            headless = false,
        )

        val iterator = args.iterator()

        while (iterator.hasNext()) {
            when (val argument = iterator.next()) {
                "--plugin-dir" -> {
                    if (iterator.hasNext()) {
                        options = options.copy(pluginDirs = options.pluginDirs + iterator.next())
                    } else {
                        error("Expected a directory path after --plugin-dir")
                    }
                }

                "--log-level" -> {
                    if (iterator.hasNext()) {
                        val logLevel = when (iterator.next()) {
                            "DEBUG" -> JetWhaleLogLevel.DEBUG
                            "INFO" -> JetWhaleLogLevel.INFO
                            "WARN" -> JetWhaleLogLevel.WARN
                            "ERROR" -> JetWhaleLogLevel.ERROR
                            else -> error("Unknown log level specified after --log-level")
                        }
                        options = options.copy(logLevel = logLevel)
                    } else {
                        error("Expected a log level after --log-level")
                    }
                }

                "--server-port" -> options = options.copy(
                    serverPortOverrides = options.serverPortOverrides.copy(serverPort = iterator.nextPort(argument)),
                )

                "--wss-port" -> options = options.copy(
                    serverPortOverrides = options.serverPortOverrides.copy(wssPort = iterator.nextPort(argument)),
                )

                "--mcp-server-port" -> options = options.copy(
                    serverPortOverrides = options.serverPortOverrides.copy(mcpServerPort = iterator.nextPort(argument)),
                )

                "--mcp-allow-all-permissions" -> options = options.copy(mcpPermissionOverride = McpPermissionOverride(allowAll = true))

                "--headless" -> options = options.copy(headless = true)

                else -> Unit
            }
        }

        return options
    }

    private fun Iterator<String>.nextPort(option: String): Int {
        if (!hasNext()) error("Expected a port number after $option")
        val rawPort = next()
        val port = rawPort.toIntOrNull()
        // Reject out-of-range values here rather than letting the server fail to bind later: the
        // failure would surface long after startup, without naming the option that caused it.
        check(port != null && port in 1..65535) { "Expected a port number in 1..65535 after $option, but was: $rawPort" }
        return port
    }
}
