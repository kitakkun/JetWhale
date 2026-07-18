package com.kitakkun.jetwhale.host.sdk

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * One MCP tool as a self-contained unit: its name, documentation, parameter schema, and
 * execution logic live in a single class.
 *
 * Parameters are declared once as delegated properties — the property name becomes the
 * parameter name shown to the AI agent, and the value is read back through the same property,
 * so a parameter has exactly one definition and its type is checked at compile time:
 * ```kotlin
 * class InspectWidgetCommand(private val widgets: WidgetStore) : JetWhaleMcpCommand() {
 *     override val name = "com.example.myplugin.inspectWidget"
 *     override val description = "Inspect the selected widget"
 *
 *     private val widgetId by string("The widget ID")
 *     private val verbose by booleanOrNull("Include layout details.")
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

    // Set once the declarations have been read (i.e. the schema may have been shown to a
    // caller); late declarations would silently diverge from it, so they throw instead.
    private var parametersSealed = false

    /** The parameters declared via the protected factory functions, in declaration order. */
    public val parameters: List<JetWhaleMcpParameter<*>>
        get() {
            parametersSealed = true
            return declaredParameters.toList()
        }

    /**
     * Executes the tool.
     *
     * @return A result string (plain text or JSON).
     */
    public abstract suspend fun execute(arguments: JetWhaleMcpArguments): String

    public fun toDescriptor(): JetWhaleMcpToolDescriptor = JetWhaleMcpToolDescriptor(
        name = name,
        description = description,
        parameters = parameters.associate { parameter ->
            parameter.name to JetWhaleMcpParameterDescriptor(
                type = parameter.type,
                description = parameter.description,
                required = parameter.required,
            )
        },
    )

    // -- Parameter declaration (use with `by` on a property; the property name is the parameter
    // name unless overridden via the `name` argument) ---------------------------------------

    protected fun string(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<String> = requiredDeclaration(name, "string", description) { _, value -> value }

    protected fun stringOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<String?> = optionalDeclaration(name, "string", description) { _, value -> value }

    protected fun int(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Int> = requiredDeclaration(name, "integer", description, ::parseInt)

    protected fun intOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Int?> = optionalDeclaration(name, "integer", description, ::parseInt)

    protected fun long(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Long> = requiredDeclaration(name, "integer", description, ::parseLong)

    protected fun longOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Long?> = optionalDeclaration(name, "integer", description, ::parseLong)

    protected fun boolean(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Boolean> = requiredDeclaration(name, "boolean", description, ::parseBoolean)

    protected fun booleanOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Boolean?> = optionalDeclaration(name, "boolean", description, ::parseBoolean)

    /** Matches [entries] by enum name, case-insensitively. */
    protected fun <T : Enum<T>> enum(description: String, entries: List<T>, name: String? = null): JetWhaleMcpParameterDeclaration<T> = requiredDeclaration(name, "string", description) { paramName, value -> parseEnum(paramName, value, entries) }

    /** Matches [entries] by enum name, case-insensitively. */
    protected fun <T : Enum<T>> enumOrNull(description: String, entries: List<T>, name: String? = null): JetWhaleMcpParameterDeclaration<T?> = optionalDeclaration(name, "string", description) { paramName, value -> parseEnum(paramName, value, entries) }

    private fun <T : Any> requiredDeclaration(name: String?, type: String, description: String, parse: (String, String) -> T): JetWhaleMcpParameterDeclaration<T> = JetWhaleMcpParameterDeclaration(
        command = this,
        explicitName = name,
        type = type,
        description = description,
        required = true,
    ) { paramName, raw ->
        raw[paramName]?.let { parse(paramName, it) }
            ?: throw JetWhaleMcpArgumentException("missing required argument: $paramName")
    }

    private fun <T : Any> optionalDeclaration(name: String?, type: String, description: String, parse: (String, String) -> T): JetWhaleMcpParameterDeclaration<T?> = JetWhaleMcpParameterDeclaration(
        command = this,
        explicitName = name,
        type = type,
        description = description,
        required = false,
    ) { paramName, raw ->
        raw[paramName]?.let { parse(paramName, it) }
    }

    internal fun <T> declare(parameter: JetWhaleMcpParameter<T>): JetWhaleMcpParameter<T> {
        check(!parametersSealed) {
            "Parameter '${parameter.name}' was declared after the parameter list of '$name' was read. Declare parameters only as property declarations on the command, never inside execute()."
        }
        check(declaredParameters.none { it.name == parameter.name }) {
            "Parameter '${parameter.name}' is declared twice on '$name'."
        }
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
 * The right-hand side of a `by` parameter declaration on a [JetWhaleMcpCommand]. Registration
 * happens in [provideDelegate], so a parameter can only come into existence as a property
 * declaration — the parameter name defaults to the property name.
 */
@ExperimentalJetWhaleApi
public class JetWhaleMcpParameterDeclaration<T> internal constructor(
    private val command: JetWhaleMcpCommand,
    private val explicitName: String?,
    private val type: String,
    private val description: String,
    private val required: Boolean,
    private val extract: (name: String, raw: Map<String, String>) -> T,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, JetWhaleMcpParameter<T>>> {
    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, JetWhaleMcpParameter<T>> {
        val parameterName = explicitName ?: property.name
        val parameter = command.declare(
            JetWhaleMcpParameter(
                name = parameterName,
                type = type,
                description = description,
                required = required,
            ) { raw -> extract(parameterName, raw) },
        )
        return ReadOnlyProperty { _, _ -> parameter }
    }
}

/**
 * A single typed parameter of a [JetWhaleMcpCommand]. Obtained by reading a `by`-declared
 * parameter property; read the value with [JetWhaleMcpArguments.get].
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
