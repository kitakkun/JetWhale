package com.kitakkun.jetwhale.host.cli

data class JetWhaleCliOptions(
    val pluginDirs: List<String>,
    val logLevel: JetWhaleLogLevel,
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
        var logLevel: JetWhaleLogLevel = JetWhaleLogLevel.WARN

        val iterator = args.iterator()

        while (iterator.hasNext()) {
            when (iterator.next()) {
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

                else -> {
                    // Ignore unknown arguments
                }
            }
        }

        return JetWhaleCliOptions(
            pluginDirs = pluginDirs,
            logLevel = logLevel,
        )
    }
}
