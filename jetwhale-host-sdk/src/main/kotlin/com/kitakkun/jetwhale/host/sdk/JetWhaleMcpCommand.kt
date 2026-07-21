package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * Besides scalars, structured arguments are available: [stringList] / [stringMap] emit
 * `array` / `object` JSON Schema types with a known element type, and [jsonObject] / [jsonArray]
 * hand back the raw [JsonElement] so a command can decode it with kotlinx.serialization.
 *
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

    // Set once the schema has been produced (and may have been shown to a caller); late
    // declarations would silently diverge from it, so they throw instead. The declarations are
    // deliberately not readable any other way: an accidental read during construction would
    // observe a half-built list.
    private var parametersSealed = false

    /**
     * Executes the tool.
     *
     * @return A result string (plain text or JSON).
     */
    public abstract suspend fun execute(arguments: JetWhaleMcpArguments): String

    public fun toDescriptor(): JetWhaleMcpToolDescriptor {
        parametersSealed = true
        return JetWhaleMcpToolDescriptor(
            name = name,
            description = description,
            parameters = declaredParameters.associate { parameter ->
                parameter.name to JetWhaleMcpParameterDescriptor(
                    type = parameter.type,
                    description = parameter.description,
                    required = parameter.required,
                    itemsType = parameter.itemsType,
                    valueType = parameter.valueType,
                )
            },
        )
    }

    // -- Scalar parameters (use with `by` on a property; the property name is the parameter
    // name unless overridden via the `name` argument) ---------------------------------------

    protected fun string(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<String> = requiredScalar(name, "string", description) { _, value -> value }

    protected fun stringOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<String?> = optionalScalar(name, "string", description) { _, value -> value }

    protected fun int(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Int> = requiredScalar(name, "integer", description, ::parseInt)

    protected fun intOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Int?> = optionalScalar(name, "integer", description, ::parseInt)

    protected fun long(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Long> = requiredScalar(name, "integer", description, ::parseLong)

    protected fun longOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Long?> = optionalScalar(name, "integer", description, ::parseLong)

    protected fun boolean(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Boolean> = requiredScalar(name, "boolean", description, ::parseBoolean)

    protected fun booleanOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Boolean?> = optionalScalar(name, "boolean", description, ::parseBoolean)

    /** Matches [entries] by enum name, case-insensitively. */
    protected fun <T : Enum<T>> enum(description: String, entries: List<T>, name: String? = null): JetWhaleMcpParameterDeclaration<T> = requiredScalar(name, "string", description) { paramName, value -> parseEnum(paramName, value, entries) }

    /** Matches [entries] by enum name, case-insensitively. */
    protected fun <T : Enum<T>> enumOrNull(description: String, entries: List<T>, name: String? = null): JetWhaleMcpParameterDeclaration<T?> = optionalScalar(name, "string", description) { paramName, value -> parseEnum(paramName, value, entries) }

    // -- Structured parameters ----------------------------------------------------------------

    /** A JSON array of strings, e.g. `["a", "b"]`. */
    protected fun stringList(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<List<String>> = requiredStructured(name, "array", description, itemsType = "string", valueType = null, parse = ::parseStringList)

    /** A JSON array of strings, e.g. `["a", "b"]`. */
    protected fun stringListOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<List<String>?> = optionalStructured(name, "array", description, itemsType = "string", valueType = null, parse = ::parseStringList)

    /** A JSON object whose values are strings, e.g. `{"Content-Type":"application/json"}`. */
    protected fun stringMap(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Map<String, String>> = requiredStructured(name, "object", description, itemsType = null, valueType = "string", parse = ::parseStringMap)

    /** A JSON object whose values are strings, e.g. `{"Content-Type":"application/json"}`. */
    protected fun stringMapOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Map<String, String>?> = optionalStructured(name, "object", description, itemsType = null, valueType = "string", parse = ::parseStringMap)

    /** A raw JSON object, handed back for the command to decode with kotlinx.serialization. */
    protected fun jsonObject(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonObject> = requiredStructured(name, "object", description, itemsType = null, valueType = null, parse = ::parseJsonObject)

    /** A raw JSON object, handed back for the command to decode with kotlinx.serialization. */
    protected fun jsonObjectOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonObject?> = optionalStructured(name, "object", description, itemsType = null, valueType = null, parse = ::parseJsonObject)

    /** A raw JSON array, handed back for the command to decode with kotlinx.serialization. */
    protected fun jsonArray(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonArray> = requiredStructured(name, "array", description, itemsType = null, valueType = null, parse = ::parseJsonArray)

    /** A raw JSON array, handed back for the command to decode with kotlinx.serialization. */
    protected fun jsonArrayOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonArray?> = optionalStructured(name, "array", description, itemsType = null, valueType = null, parse = ::parseJsonArray)

    // -- Declaration builders -----------------------------------------------------------------

    private fun <T : Any> requiredScalar(name: String?, type: String, description: String, parse: (String, String) -> T): JetWhaleMcpParameterDeclaration<T> = requiredStructured(name, type, description, itemsType = null, valueType = null) { paramName, element ->
        parse(paramName, scalarContent(paramName, element))
    }

    private fun <T : Any> optionalScalar(name: String?, type: String, description: String, parse: (String, String) -> T): JetWhaleMcpParameterDeclaration<T?> = optionalStructured(name, type, description, itemsType = null, valueType = null) { paramName, element ->
        parse(paramName, scalarContent(paramName, element))
    }

    private fun <T : Any> requiredStructured(name: String?, type: String, description: String, itemsType: String?, valueType: String?, parse: (String, JsonElement) -> T): JetWhaleMcpParameterDeclaration<T> = JetWhaleMcpParameterDeclaration(
        command = this,
        explicitName = name,
        type = type,
        description = description,
        required = true,
        itemsType = itemsType,
        valueType = valueType,
    ) { paramName, raw ->
        val element = raw[paramName]?.takeUnless { it is JsonNull }
            ?: throw JetWhaleMcpArgumentException("missing required argument: $paramName")
        parse(paramName, element)
    }

    private fun <T : Any> optionalStructured(name: String?, type: String, description: String, itemsType: String?, valueType: String?, parse: (String, JsonElement) -> T): JetWhaleMcpParameterDeclaration<T?> = JetWhaleMcpParameterDeclaration(
        command = this,
        explicitName = name,
        type = type,
        description = description,
        required = false,
        itemsType = itemsType,
        valueType = valueType,
    ) { paramName, raw ->
        raw[paramName]?.takeUnless { it is JsonNull }?.let { parse(paramName, it) }
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

    private fun scalarContent(name: String, element: JsonElement): String = (element as? JsonPrimitive)?.content
        ?: throw JetWhaleMcpArgumentException("invalid $name: expected a scalar value")

    private fun parseInt(name: String, value: String): Int = value.toIntOrNull() ?: invalid(name, value, "an integer")

    private fun parseLong(name: String, value: String): Long = value.toLongOrNull() ?: invalid(name, value, "an integer")

    private fun parseBoolean(name: String, value: String): Boolean = value.toBooleanStrictOrNull() ?: invalid(name, value, "true or false")

    private fun <T : Enum<T>> parseEnum(name: String, value: String, entries: List<T>): T = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: invalid(name, value, "one of ${entries.joinToString(", ") { it.name }}")

    private fun parseStringList(name: String, element: JsonElement): List<String> {
        val array = element as? JsonArray ?: throw JetWhaleMcpArgumentException("invalid $name: expected a JSON array")
        return array.map { item ->
            (item as? JsonPrimitive)?.content ?: throw JetWhaleMcpArgumentException("invalid $name: expected an array of strings")
        }
    }

    private fun parseStringMap(name: String, element: JsonElement): Map<String, String> {
        val obj = element as? JsonObject ?: throw JetWhaleMcpArgumentException("invalid $name: expected a JSON object")
        return obj.mapValues { (_, value) ->
            (value as? JsonPrimitive)?.content ?: throw JetWhaleMcpArgumentException("invalid $name: expected an object of string values")
        }
    }

    private fun parseJsonObject(name: String, element: JsonElement): JsonObject = element as? JsonObject
        ?: throw JetWhaleMcpArgumentException("invalid $name: expected a JSON object")

    private fun parseJsonArray(name: String, element: JsonElement): JsonArray = element as? JsonArray
        ?: throw JetWhaleMcpArgumentException("invalid $name: expected a JSON array")

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
    private val itemsType: String?,
    private val valueType: String?,
    private val extract: (name: String, raw: JsonObject) -> T,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, JetWhaleMcpParameter<T>>> {
    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, JetWhaleMcpParameter<T>> {
        val parameterName = explicitName ?: property.name
        val parameter = command.declare(
            JetWhaleMcpParameter(
                name = parameterName,
                type = type,
                description = description,
                required = required,
                itemsType = itemsType,
                valueType = valueType,
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
    // For type "array": the JSON Schema type of each element. Null when the element type is
    // unconstrained (raw arrays).
    public val itemsType: String?,
    // For type "object": the JSON Schema type of each value. Null when the value type is
    // unconstrained (raw objects).
    public val valueType: String?,
    private val extract: (JsonObject) -> T,
) {
    internal fun extractFrom(raw: JsonObject): T = extract(raw)
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
public class JetWhaleMcpArguments(private val raw: JsonObject) {
    public operator fun <T> get(parameter: JetWhaleMcpParameter<T>): T = parameter.extractFrom(raw)
}
