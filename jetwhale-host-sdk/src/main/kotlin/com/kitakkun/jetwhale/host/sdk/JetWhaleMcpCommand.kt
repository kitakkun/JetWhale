package com.kitakkun.jetwhale.host.sdk

/**
 * One MCP tool as a self-contained unit: its name, documentation, parameter schema, and
 * execution logic live in a single class, so the schema and the code reading the arguments
 * cannot drift apart, and each command can be unit-tested in isolation.
 *
 * Implement commands and expose them through [JetWhaleMcpCapablePlugin]:
 * ```kotlin
 * class InspectWidgetCommand(private val widgets: WidgetStore) : JetWhaleMcpCommand() {
 *     override val name = "com.example.myplugin.inspectWidget"
 *     override val description = "Inspect the selected widget"
 *     override val parameters = mapOf(
 *         "widgetId" to JetWhaleMcpParameterDescriptor("string", "The widget ID"),
 *     )
 *
 *     override suspend fun execute(arguments: JetWhaleMcpArguments): String {
 *         val widgetId = arguments.requireString("widgetId")
 *         return widgets.describeAsJson(widgetId)
 *     }
 * }
 * ```
 */
@ExperimentalJetWhaleApi
public abstract class JetWhaleMcpCommand {
    /** Globally unique tool name; by convention prefixed with the pluginId. */
    public abstract val name: String

    /** Human-readable description shown to the AI agent. */
    public abstract val description: String

    /** Parameter descriptors keyed by parameter name. Defaulted because many tools take none. */
    public open val parameters: Map<String, JetWhaleMcpParameterDescriptor> get() = emptyMap()

    /**
     * Executes the tool. Throw [JetWhaleMcpArgumentException] (the [JetWhaleMcpArguments]
     * accessors do this for you) to report a caller mistake; it is rendered as an
     * `{"error": ...}` payload instead of failing the MCP server.
     *
     * @return A result string (plain text or JSON).
     */
    public abstract suspend fun execute(arguments: JetWhaleMcpArguments): String

    public fun toDescriptor(): JetWhaleMcpToolDescriptor = JetWhaleMcpToolDescriptor(
        name = name,
        description = description,
        parameters = parameters,
    )
}

/** A caller mistake in a tool invocation (missing/invalid argument, unknown id, ...). */
@ExperimentalJetWhaleApi
public class JetWhaleMcpArgumentException(message: String) : Exception(message)

/**
 * Typed access to the raw string arguments of an MCP tool call. All `require*`/`optional*`
 * accessors throw [JetWhaleMcpArgumentException] with a caller-facing message when an argument
 * is missing or cannot be parsed.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpArguments(private val raw: Map<String, String>) {
    public fun optionalString(name: String): String? = raw[name]

    public fun requireString(name: String): String = raw[name] ?: missing(name)

    public fun optionalInt(name: String): Int? = raw[name]?.let { it.toIntOrNull() ?: invalid(name, it, "an integer") }

    public fun requireInt(name: String): Int = optionalInt(name) ?: missing(name)

    public fun optionalLong(name: String): Long? = raw[name]?.let { it.toLongOrNull() ?: invalid(name, it, "an integer") }

    public fun requireLong(name: String): Long = optionalLong(name) ?: missing(name)

    public fun optionalBoolean(name: String): Boolean? = raw[name]?.let { it.toBooleanStrictOrNull() ?: invalid(name, it, "true or false") }

    public fun requireBoolean(name: String): Boolean = optionalBoolean(name) ?: missing(name)

    /** Matches [entries] by enum name, case-insensitively. */
    public fun <T : Enum<T>> optionalEnum(name: String, entries: List<T>): T? = raw[name]?.let { value ->
        entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: invalid(name, value, "one of ${entries.joinToString(", ") { it.name }}")
    }

    public fun <T : Enum<T>> requireEnum(name: String, entries: List<T>): T = optionalEnum(name, entries) ?: missing(name)

    private fun missing(name: String): Nothing = throw JetWhaleMcpArgumentException("missing required argument: $name")

    private fun invalid(name: String, value: String, expected: String): Nothing = throw JetWhaleMcpArgumentException("invalid $name: $value (expected $expected)")
}
