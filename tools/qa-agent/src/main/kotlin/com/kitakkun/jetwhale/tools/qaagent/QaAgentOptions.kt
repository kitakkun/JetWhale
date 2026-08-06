package com.kitakkun.jetwhale.tools.qaagent

internal const val DEFAULT_CONTROL_PORT = 7100
internal const val DEFAULT_HOST_PORT = 5443
internal const val DEFAULT_PLUGIN_VERSION = "1.0.0"

/** App name used when the run does not ask for any, so the common single-session case needs no flag. */
internal const val DEFAULT_APP_NAME = "qa-agent"

/**
 * Id of the Network agent plugin every app carries so `/fire` has something to capture traffic with.
 * A wire-level stand-in must never be registered under it: the two would claim the same id in one
 * session, and the stand-in — which registers no request handlers — would shadow the real plugin's,
 * leaving mocking unreachable. Pinned against the real plugin by `QaAgentOptionsTest`.
 */
internal const val BUILT_IN_NETWORK_PLUGIN_ID = "com.kitakkun.jetwhale.network"

/**
 * What the agent impersonates and where it connects.
 *
 * @param apps names of the apps to connect as, one session each. Never empty.
 * @param plugins plugin ids to register a [WireLevelQaPlugin] for, each with the version to report.
 *   Every app registers the same set, so the same plugin can be driven per session.
 */
internal data class QaAgentOptions(
    val apps: List<String>,
    val plugins: Map<String, String>,
    val hostName: String,
    val hostPort: Int,
    val controlPort: Int,
)

internal val usage = """
    Usage: qa-agent [options]

      --app <name>               Connect as an app of this name, as its own session. Repeatable, so
                                 one process can hold several apps under one device — which is how
                                 the host groups them. Default: one app named $DEFAULT_APP_NAME.
      --plugin <id>[@<version>]  Register a raw-messaging plugin under this id, so /send and
                                 /request can drive its host counterpart. Registered for every app.
                                 Repeatable. Version defaults to $DEFAULT_PLUGIN_VERSION.
                                 $BUILT_IN_NETWORK_PLUGIN_ID is rejected: it is always registered.
      --host <name>              JetWhale host to connect to (default: localhost).
      --port <n>                 Host debug port (default: $DEFAULT_HOST_PORT).
      --control-port <n>         Port for this agent's own control API (default: $DEFAULT_CONTROL_PORT).
""".trimIndent()

internal fun parseArgs(args: Array<String>): QaAgentOptions {
    val apps = mutableListOf<String>()
    val plugins = mutableMapOf<String, String>()
    var hostName = "localhost"
    var hostPort = DEFAULT_HOST_PORT
    var controlPort = DEFAULT_CONTROL_PORT

    fun valueOf(index: Int, name: String): String = args.getOrNull(index) ?: error("$name requires a value.\n\n$usage")

    fun intValueOf(index: Int, name: String): Int = valueOf(index, name).toIntOrNull() ?: error("$name requires a number.\n\n$usage")

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--app" -> {
                val name = valueOf(++i, arg)
                // Names key every control call, so a duplicate would make one of the two sessions
                // unreachable rather than merely confusing.
                require(name !in apps) { "--app $name was given twice; app names address sessions and must be unique.\n\n$usage" }
                apps += name
            }

            "--plugin" -> {
                val (id, version) = valueOf(++i, arg).split("@", limit = 2).let {
                    it[0] to (it.getOrNull(1) ?: DEFAULT_PLUGIN_VERSION)
                }
                require(id != BUILT_IN_NETWORK_PLUGIN_ID) {
                    "--plugin $id is already built in; registering it again would shadow its request " +
                        "handlers and leave mocking unreachable. Drop the flag — the plugin is always on.\n\n$usage"
                }
                plugins[id] = version
            }

            "--host" -> hostName = valueOf(++i, arg)

            "--port" -> hostPort = intValueOf(++i, arg)

            "--control-port" -> controlPort = intValueOf(++i, arg)

            "--help", "-h" -> throw HelpRequestedException()

            else -> error("Unknown option: $arg\n\n$usage")
        }
        i++
    }

    return QaAgentOptions(
        apps = apps.ifEmpty { listOf(DEFAULT_APP_NAME) },
        plugins = plugins,
        hostName = hostName,
        hostPort = hostPort,
        controlPort = controlPort,
    )
}

/** Thrown for `--help`, so parsing stays free of process exits and can be tested. */
internal class HelpRequestedException : RuntimeException()
