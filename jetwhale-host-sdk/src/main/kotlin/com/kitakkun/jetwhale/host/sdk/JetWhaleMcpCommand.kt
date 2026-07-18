package com.kitakkun.jetwhale.host.sdk

/**
 * One MCP tool as a self-contained unit: its name, documentation, parameter schema, and
 * execution logic live in a single class.
 *
 * Parameters are declared once as typed properties via the protected factory functions, and read
 * back through the same property — the schema shown to the AI agent and the code consuming the
 * arguments cannot drift apart, and there are no repeated string keys:
 * ```kotlin
 * class InspectWidgetCommand(private val widgets: WidgetStore) : JetWhaleMcpCommand() {
 *     override val name = "com.example.myplugin.inspectWidget"
 *     override val description = "Inspect the selected widget"
 *
 *     private val widgetId = requiredString("widgetId", "The widget ID")
 *     private val verbose = optionalBoolean("verbose", "Include layout details.")
 *
 *     override suspend fun execute(arguments: JetWhaleMcpArguments): String {
 *         return widgets.describeAsJson(id = arguments[widgetId], verbose = arguments[verbose] ?: false)
 *     }
 * }
 * ```
 * Expose commands through [JetWhaleMcpCapablePlugin]. A [JetWhaleMcpArgumentException] (thrown
 * by the argument accessors, or by [execute] directly for domain-level caller mistakes) is
 * rendered as an `{"error": ...}` payload instead of failing the MCP server.
 */
@ExperimentalJetWhaleApi
public abstract class JetWhaleMcpCommand {
    /** Globally unique tool name; by convention prefixed with the pluginId. */
    public abstract val name: String

    /** Human-readable description shown to the AI agent. */
    public abstract val description: String

    private val declaredParameters = mutableListOf<JetWhaleMcpParameter<*>>()

    /** The parameters declared via the protected factory functions, in declaration order. */
    public val parameters: List<JetWhaleMcpParameter<*>> get() = declaredParameters

    /**
     * Executes the tool.
     *
     * @return A result string (plain text or JSON).
     */
    public abstract suspend fun execute(arguments: JetWhaleMcpArguments): String

    public fun toDescriptor(): JetWhaleMcpToolDescriptor = JetWhaleMcpToolDescriptor(
        name = name,
        description = description,
        parameters = declaredParameters.associate { parameter ->
            parameter.name to JetWhaleMcpParameterDescriptor(
                type = parameter.type,
                description = parameter.description,
                required = parameter.required,
            )
        },
    )

    // -- Parameter declaration (call from property initializers) ------------------------------

    protected fun requiredString(name: String, description: String): JetWhaleMcpParameter<String> = required(name, "string", description) { it }

    protected fun optionalString(name: String, description: String): JetWhaleMcpParameter<String?> = optional(name, "string", description) { it }

    protected fun requiredInt(name: String, description: String): JetWhaleMcpParameter<Int> = required(name, "integer", description) { parseInt(name, it) }

    protected fun optionalInt(name: String, description: String): JetWhaleMcpParameter<Int?> = optional(name, "integer", description) { parseInt(name, it) }

    protected fun requiredLong(name: String, description: String): JetWhaleMcpParameter<Long> = required(name, "integer", description) { parseLong(name, it) }

    protected fun optionalLong(name: String, description: String): JetWhaleMcpParameter<Long?> = optional(name, "integer", description) { parseLong(name, it) }

    protected fun requiredBoolean(name: String, description: String): JetWhaleMcpParameter<Boolean> = required(name, "boolean", description) { parseBoolean(name, it) }

    protected fun optionalBoolean(name: String, description: String): JetWhaleMcpParameter<Boolean?> = optional(name, "boolean", description) { parseBoolean(name, it) }

    /** Matches [entries] by enum name, case-insensitively. */
    protected fun <T : Enum<T>> requiredEnum(name: String, description: String, entries: List<T>): JetWhaleMcpParameter<T> = required(name, "string", description) { parseEnum(name, it, entries) }

    /** Matches [entries] by enum name, case-insensitively. */
    protected fun <T : Enum<T>> optionalEnum(name: String, description: String, entries: List<T>): JetWhaleMcpParameter<T?> = optional(name, "string", description) { parseEnum(name, it, entries) }

    private fun <T : Any> required(name: String, type: String, description: String, parse: (String) -> T): JetWhaleMcpParameter<T> = declare(
        JetWhaleMcpParameter(name = name, type = type, description = description, required = true) { raw ->
            raw[name]?.let(parse) ?: throw JetWhaleMcpArgumentException("missing required argument: $name")
        },
    )

    private fun <T : Any> optional(name: String, type: String, description: String, parse: (String) -> T): JetWhaleMcpParameter<T?> = declare(
        JetWhaleMcpParameter(name = name, type = type, description = description, required = false) { raw ->
            raw[name]?.let(parse)
        },
    )

    private fun <T> declare(parameter: JetWhaleMcpParameter<T>): JetWhaleMcpParameter<T> {
        declaredParameters.add(parameter)
        return parameter
    }

    private fun parseInt(name: String, value: String): Int = value.toIntOrNull() ?: invalid(name, value, "an integer")

    private fun parseLong(name: String, value: String): Long = value.toLongOrNull() ?: invalid(name, value, "an integer")

    private fun parseBoolean(name: String, value: String): Boolean = value.toBooleanStrictOrNull() ?: invalid(name, value, "true or false")

    private fun <T : Enum<T>> parseEnum(name: String, value: String, entries: List<T>): T = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: invalid(name, value, "one of ${entries.joinToString(", ") { it.name }}")

    private fun invalid(name: String, value: String, expected: String): Nothing = throw JetWhaleMcpArgumentException("invalid $name: $value (expected $expected)")
}

/**
 * A single typed parameter of a [JetWhaleMcpCommand]. Created via the command's protected
 * factory functions; read with [JetWhaleMcpArguments.get].
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpParameter<T> internal constructor(
    public val name: String,
    public val type: String,
    public val description: String,
    public val required: Boolean,
    private val extract: (Map<String, String>) -> T,
) {
    internal fun extractFrom(raw: Map<String, String>): T = extract(raw)
}

/** A caller mistake in a tool invocation (missing/invalid argument, unknown id, ...). */
@ExperimentalJetWhaleApi
public class JetWhaleMcpArgumentException(message: String) : Exception(message)

/**
 * The raw arguments of an MCP tool call, read through the command's declared
 * [JetWhaleMcpParameter]s: `arguments[myParam]` returns the parameter's typed value and throws
 * [JetWhaleMcpArgumentException] with a caller-facing message when it is missing or unparseable.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpArguments(private val raw: Map<String, String>) {
    public operator fun <T> get(parameter: JetWhaleMcpParameter<T>): T = parameter.extractFrom(raw)
}
