package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
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
 *     override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
 *         return JetWhaleMcpResult.json(widgets.describe(id = arguments[widgetId], verbose = arguments[verbose] ?: false))
 *     }
 * }
 * ```
 * Besides scalars, structured arguments are available. [serializable] is the one to reach for when
 * the shape is known: it decodes the argument into a `@Serializable` type and derives the
 * parameter's JSON Schema from that type, so the shape never has to be restated in prose.
 * [stringList] / [stringMap] cover the common flat containers, and [jsonObject] / [jsonArray] hand
 * back the raw [JsonElement] for payloads whose shape is not known ahead of time.
 *
 * [execute] answers with a [JetWhaleMcpResult] — text, structured JSON, an image, or a failure.
 * A command that only ever answers with text can extend [JetWhaleMcpTextCommand] instead and
 * return the string directly.
 *
 * Expose commands through [JetWhaleMcpCapablePlugin]. A [JetWhaleMcpArgumentException] (thrown
 * by the argument accessors, or by [execute] directly for domain-level caller mistakes) becomes a
 * failed [JetWhaleMcpResult] the agent can read and correct, instead of failing the MCP server.
 *
 * @param json Format used to decode [serializable] arguments and to derive their schema; also
 *   available to [execute] for encoding results. Defaults to [DefaultArgumentJson]. Pass a custom
 *   instance to register a [kotlinx.serialization.modules.SerializersModule] (needed for contextual
 *   or open polymorphic types) or to change the class discriminator or naming strategy — the
 *   derived schema follows the instance, so the two cannot drift apart.
 */
@ExperimentalJetWhaleApi
public abstract class JetWhaleMcpCommand(
    // A constructor parameter rather than an open val: parameters are declared as property
    // initializers, which run before a subclass' own property overrides would be assigned.
    protected val json: Json = DefaultArgumentJson,
) {
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
     * @return What the AI agent receives — build it with the [JetWhaleMcpResult] factories.
     */
    public abstract suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult

    public fun toDescriptor(): JetWhaleMcpToolDescriptor {
        parametersSealed = true
        return JetWhaleMcpToolDescriptor(
            name = name,
            description = description,
            parameters = declaredParameters.associate { parameter ->
                parameter.name to JetWhaleMcpParameterDescriptor(
                    schema = parameter.schema,
                    description = parameter.description,
                    required = parameter.required,
                )
            },
        )
    }

    // -- Scalar parameters (use with `by` on a property; the property name is the parameter
    // name unless overridden via the `name` argument) ---------------------------------------

    protected fun string(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<String> = requiredScalar(name, STRING_SCHEMA, description) { _, value -> value }

    protected fun stringOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<String?> = optionalScalar(name, STRING_SCHEMA, description) { _, value -> value }

    protected fun int(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Int> = requiredScalar(name, INTEGER_SCHEMA, description, ::parseInt)

    protected fun intOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Int?> = optionalScalar(name, INTEGER_SCHEMA, description, ::parseInt)

    protected fun long(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Long> = requiredScalar(name, INTEGER_SCHEMA, description, ::parseLong)

    protected fun longOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Long?> = optionalScalar(name, INTEGER_SCHEMA, description, ::parseLong)

    protected fun boolean(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Boolean> = requiredScalar(name, BOOLEAN_SCHEMA, description, ::parseBoolean)

    protected fun booleanOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Boolean?> = optionalScalar(name, BOOLEAN_SCHEMA, description, ::parseBoolean)

    /** Matches [entries] by enum name, case-insensitively; the entry names are advertised as the schema's `enum`. */
    protected fun <T : Enum<T>> enum(description: String, entries: List<T>, name: String? = null): JetWhaleMcpParameterDeclaration<T> = requiredScalar(name, enumSchema(entries), description) { paramName, value -> parseEnum(paramName, value, entries) }

    /** Matches [entries] by enum name, case-insensitively; the entry names are advertised as the schema's `enum`. */
    protected fun <T : Enum<T>> enumOrNull(description: String, entries: List<T>, name: String? = null): JetWhaleMcpParameterDeclaration<T?> = optionalScalar(name, enumSchema(entries), description) { paramName, value -> parseEnum(paramName, value, entries) }

    // -- Structured parameters ----------------------------------------------------------------

    /**
     * A value decoded into the `@Serializable` type [T]. The parameter's JSON Schema is derived
     * from [T]'s serializer — nested objects, enum entries and which properties are required all
     * come from the type itself, so the tool's description does not have to spell the shape out.
     *
     * Unknown JSON keys are ignored, so a value the agent read back from another tool round-trips
     * even if it carries annotations of its own. A payload that does not fit [T] raises a
     * [JetWhaleMcpArgumentException] naming the parameter.
     */
    protected inline fun <reified T : Any> serializable(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<T> = serializable(serializer<T>(), description, name)

    /** @see serializable */
    protected inline fun <reified T : Any> serializableOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<T?> = serializableOrNull(serializer<T>(), description, name)

    /** Explicit-serializer form of [serializable], for types whose serializer cannot be resolved from the type argument. */
    protected fun <T : Any> serializable(serializer: KSerializer<T>, description: String, name: String? = null): JetWhaleMcpParameterDeclaration<T> = requiredStructured(name, serializer.descriptor.toJsonSchema(json), description) { paramName, element -> decode(paramName, serializer, element) }

    /** Explicit-serializer form of [serializableOrNull]. */
    protected fun <T : Any> serializableOrNull(serializer: KSerializer<T>, description: String, name: String? = null): JetWhaleMcpParameterDeclaration<T?> = optionalStructured(name, serializer.descriptor.toJsonSchema(json), description) { paramName, element -> decode(paramName, serializer, element) }

    /** A JSON array of strings, e.g. `["a", "b"]`. */
    protected fun stringList(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<List<String>> = requiredStructured(name, STRING_LIST_SCHEMA, description, parse = ::parseStringList)

    /** A JSON array of strings, e.g. `["a", "b"]`. */
    protected fun stringListOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<List<String>?> = optionalStructured(name, STRING_LIST_SCHEMA, description, parse = ::parseStringList)

    /** A JSON object whose values are strings, e.g. `{"Content-Type":"application/json"}`. */
    protected fun stringMap(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Map<String, String>> = requiredStructured(name, STRING_MAP_SCHEMA, description, parse = ::parseStringMap)

    /** A JSON object whose values are strings, e.g. `{"Content-Type":"application/json"}`. */
    protected fun stringMapOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<Map<String, String>?> = optionalStructured(name, STRING_MAP_SCHEMA, description, parse = ::parseStringMap)

    /**
     * A raw JSON object, for payloads whose shape is not known ahead of time. The schema advertises
     * only `object`, so prefer [serializable] whenever the shape is known.
     */
    protected fun jsonObject(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonObject> = requiredStructured(name, OBJECT_SCHEMA, description, parse = ::parseJsonObject)

    /** @see jsonObject */
    protected fun jsonObjectOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonObject?> = optionalStructured(name, OBJECT_SCHEMA, description, parse = ::parseJsonObject)

    /**
     * A raw JSON array, for payloads whose shape is not known ahead of time. The schema advertises
     * only `array`, so prefer [serializable] whenever the shape is known.
     */
    protected fun jsonArray(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonArray> = requiredStructured(name, ARRAY_SCHEMA, description, parse = ::parseJsonArray)

    /** @see jsonArray */
    protected fun jsonArrayOrNull(description: String, name: String? = null): JetWhaleMcpParameterDeclaration<JsonArray?> = optionalStructured(name, ARRAY_SCHEMA, description, parse = ::parseJsonArray)

    // -- Declaration builders -----------------------------------------------------------------

    private fun <T : Any> requiredScalar(name: String?, schema: JsonObject, description: String, parse: (String, String) -> T): JetWhaleMcpParameterDeclaration<T> = requiredStructured(name, schema, description) { paramName, element ->
        parse(paramName, scalarContent(paramName, element))
    }

    private fun <T : Any> optionalScalar(name: String?, schema: JsonObject, description: String, parse: (String, String) -> T): JetWhaleMcpParameterDeclaration<T?> = optionalStructured(name, schema, description) { paramName, element ->
        parse(paramName, scalarContent(paramName, element))
    }

    private fun <T : Any> requiredStructured(name: String?, schema: JsonObject, description: String, parse: (String, JsonElement) -> T): JetWhaleMcpParameterDeclaration<T> = JetWhaleMcpParameterDeclaration(
        command = this,
        explicitName = name,
        schema = schema,
        description = description,
        required = true,
    ) { paramName, raw ->
        val element = raw[paramName]?.takeUnless { it is JsonNull }
            ?: throw JetWhaleMcpArgumentException("missing required argument: $paramName")
        parse(paramName, element)
    }

    private fun <T : Any> optionalStructured(name: String?, schema: JsonObject, description: String, parse: (String, JsonElement) -> T): JetWhaleMcpParameterDeclaration<T?> = JetWhaleMcpParameterDeclaration(
        command = this,
        explicitName = name,
        schema = schema,
        description = description,
        required = false,
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

    // SerializationException is an IllegalArgumentException, so this covers both a malformed
    // payload and a value that does not fit the target type.
    private fun <T : Any> decode(name: String, serializer: KSerializer<T>, element: JsonElement): T = try {
        json.decodeFromJsonElement(serializer, element)
    } catch (e: IllegalArgumentException) {
        throw JetWhaleMcpArgumentException("invalid $name: ${e.message}")
    }

    private fun invalid(name: String, value: String, expected: String): Nothing = throw JetWhaleMcpArgumentException("invalid $name: $value (expected $expected)")

    private companion object {
        val STRING_SCHEMA = buildJsonObject { put("type", "string") }
        val INTEGER_SCHEMA = buildJsonObject { put("type", "integer") }
        val BOOLEAN_SCHEMA = buildJsonObject { put("type", "boolean") }
        val OBJECT_SCHEMA = buildJsonObject { put("type", "object") }
        val ARRAY_SCHEMA = buildJsonObject { put("type", "array") }

        val STRING_LIST_SCHEMA = buildJsonObject {
            put("type", "array")
            putJsonObject("items") { put("type", "string") }
        }

        val STRING_MAP_SCHEMA = buildJsonObject {
            put("type", "object")
            putJsonObject("additionalProperties") { put("type", "string") }
        }

        fun <T : Enum<T>> enumSchema(entries: List<T>): JsonObject = buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { entries.forEach { add(it.name) } }
        }
    }
}

/**
 * A [JetWhaleMcpCommand] whose answer is always plain text, so it returns the string itself:
 * ```kotlin
 * class DescribeWidgetCommand(private val widgets: WidgetStore) : JetWhaleMcpTextCommand() {
 *     override val name = "com.example.myplugin.describeWidget"
 *     override val description = "Describe the selected widget"
 *
 *     private val widgetId by string("The widget ID")
 *
 *     override suspend fun executeText(arguments: JetWhaleMcpArguments): String = widgets.describe(arguments[widgetId])
 * }
 * ```
 * Extend [JetWhaleMcpCommand] directly to report a failure, structured JSON, or an image.
 *
 * @param json Same meaning as on [JetWhaleMcpCommand], and defaulted the same way.
 */
@ExperimentalJetWhaleApi
public abstract class JetWhaleMcpTextCommand(json: Json = DefaultArgumentJson) : JetWhaleMcpCommand(json) {
    final override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.text(executeText(arguments))

    /**
     * Executes the tool.
     *
     * @return The text handed to the AI agent. Throw [JetWhaleMcpArgumentException] to report a
     *   caller mistake.
     */
    protected abstract suspend fun executeText(arguments: JetWhaleMcpArguments): String
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
    private val schema: JsonObject,
    private val description: String,
    private val required: Boolean,
    private val extract: (name: String, raw: JsonObject) -> T,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, JetWhaleMcpParameter<T>>> {
    override fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, JetWhaleMcpParameter<T>> {
        val parameterName = explicitName ?: property.name
        val parameter = command.declare(
            JetWhaleMcpParameter(
                name = parameterName,
                schema = schema,
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
    // JSON Schema fragment for the values this parameter accepts; carries no "description" of its
    // own, which the MCP server merges in from [description].
    public val schema: JsonObject,
    public val description: String,
    public val required: Boolean,
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
