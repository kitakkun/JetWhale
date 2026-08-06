package com.kitakkun.jetwhale.host.cli

import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.ServerPortOverrides

data class JetWhaleCliOptions(
    val pluginDirs: List<String>,
    /**
     * Null when `--log-level` was not passed, which leaves whatever `logback.xml` configures in
     * place. Applying a default here would quietly reduce what the host logs — and so what the log
     * viewer can show — for every launch that never asked for it.
     */
    val logLevel: JetWhaleLogLevel?,
    val serverPortOverrides: ServerPortOverrides,
    val mcpPermissionOverride: McpPermissionOverride,
    /** Runs the servers with no window, for CI and agent-driven QA. See [com.kitakkun.jetwhale.host.headless.HeadlessHostRunner]. */
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
        val pluginDirs = mutableListOf<String>()
        var logLevel: JetWhaleLogLevel? = null
        var serverPort: Int? = null
        var wssPort: Int? = null
        var mcpServerPort: Int? = null
        var mcpAllowAllPermissions = false
        var headless = false

        val iterator = args.iterator()

        while (iterator.hasNext()) {
            when (val argument = iterator.next()) {
                "--plugin-dir" -> {
                    if (iterator.hasNext()) {
                        pluginDirs.add(iterator.next())
                    } else {
                        error("Expected a directory path after --plugin-dir")
                    }
                }

                "--log-level" -> {
                    if (iterator.hasNext()) {
                        logLevel = when (iterator.next()) {
                            "DEBUG" -> JetWhaleLogLevel.DEBUG
                            "INFO" -> JetWhaleLogLevel.INFO
                            "WARN" -> JetWhaleLogLevel.WARN
                            "ERROR" -> JetWhaleLogLevel.ERROR
                            else -> error("Unknown log level specified after --log-level")
                        }
                    } else {
                        error("Expected a log level after --log-level")
                    }
                }

                "--server-port" -> serverPort = iterator.nextPort(argument)

                "--wss-port" -> wssPort = iterator.nextPort(argument)

                "--mcp-server-port" -> mcpServerPort = iterator.nextPort(argument)

                "--mcp-allow-all-permissions" -> mcpAllowAllPermissions = true

                "--headless" -> headless = true

                else -> {
                    // Ignore unknown arguments
                }
            }
        }

        return JetWhaleCliOptions(
            pluginDirs = pluginDirs,
            logLevel = logLevel,
            serverPortOverrides = ServerPortOverrides(
                serverPort = serverPort,
                wssPort = wssPort,
                mcpServerPort = mcpServerPort,
            ),
            mcpPermissionOverride = McpPermissionOverride(allowAll = mcpAllowAllPermissions),
            headless = headless,
        )
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
