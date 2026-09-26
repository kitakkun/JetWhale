package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/**
 * One call that went through the recording proxy.
 *
 * @property expectations Checks the recording agent attached to this call; the exported step keeps
 *   them, and keeps the step even when it only reads.
 */
data class RecordedCall(
    val server: String,
    val tool: String,
    val arguments: JsonObject,
    val document: JsonElement,
    val isError: Boolean,
    val readOnly: Boolean,
    val expectations: List<Expectation>,
)

/**
 * How to turn a recording into a workflow.
 *
 * @property dropReads Leave out read-only calls whose results no later call uses — the looking-around
 *   an agent does before acting.
 * @property parameters Literal values to turn into workflow inputs: input name to the recorded value.
 * @property multipleServers Name the server on every step; needed when the recording spans several.
 */
data class ExportOptions(
    val name: String,
    val description: String?,
    val dropReads: Boolean,
    val parameters: Map<String, JsonElement>,
    val multipleServers: Boolean,
)

/** Field names that identify an item in a list well enough to find it again in a later run. */
private val IdentifyingFields = listOf(
    "testTag", "text", "name", "title", "label", "contentDescription", "appName", "sessionName", "deviceName", "key", "email",
)

/** Boolean fields that mark an item as the live one among entries that otherwise look alike. */
private val LivenessFlags = listOf("isActive", "active", "connected", "isConnected", "online", "isCurrent", "current")

/** Strings shorter than this are too likely to repeat by coincidence to be treated as data flow. */
private const val MIN_TRACKED_STRING_LENGTH = 6

private val IdLikeKey = Regex("""(?i)(^id$|id$|Id$|uuid|token|handle)""")

/**
 * Turns recorded calls into a workflow that can run again.
 *
 * A recording is full of values that change every run — session ids, node ids, generated records.
 * Where a call's argument equals a value an earlier call returned, the earlier step gets a `save` and
 * the argument becomes `${variable}`, so the replay picks up that run's value. A path through a list
 * is written as a filter on a field that identifies the item (its text, name, test tag…) rather than
 * an index, since order is what changes most between runs. An `...Id` argument whose value nothing
 * returned becomes an input with the recorded value as default, flagged as likely to change.
 */
fun exportWorkflow(calls: List<RecordedCall>, options: ExportOptions): Workflow = RecordingExport(calls, options).export()

private class RecordingExport(private val calls: List<RecordedCall>, private val options: ExportOptions) {
    private val steps = calls.mapIndexed { index, call ->
        val base = call.tool.substringAfterLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
        val occurrence = calls.take(index).count { it.tool == call.tool }
        call.toMutableStep(id = if (occurrence == 0) base else "$base-${occurrence + 1}")
    }
    private val inputs = linkedMapOf<String, InputSpec>()
    private val variableNames = mutableSetOf<String>()

    fun export(): Workflow {
        options.parameters.forEach { (name, value) ->
            inputs[name] = InputSpec(type = value.jsonType(), description = "recorded as ${value.render()}", default = value)
            variableNames += name
        }
        calls.forEachIndexed { index, call ->
            steps[index].args = rewrite(call.arguments, argumentKey = null) { key, value -> templateFor(index, key, value) } as JsonObject
        }
        val kept = steps.filterIndexed { index, step -> !options.dropReads || !calls[index].readOnly || step.keep }
        return Workflow(
            version = WORKFLOW_FORMAT_VERSION,
            name = options.name,
            description = options.description,
            inputs = inputs,
            steps = kept.map { step ->
                Step(
                    id = step.id,
                    server = step.server.takeIf { options.multipleServers },
                    call = step.tool,
                    args = step.args,
                    save = step.save,
                    expect = step.expect,
                )
            },
        )
    }

    /** The template that replaces [value] in call [index]'s arguments, or null to keep the literal. */
    private fun templateFor(index: Int, key: String?, value: JsonPrimitive): String? {
        options.parameters.entries.firstOrNull { (_, parameter) -> jsonEquals(parameter, value) }?.let { return "\${${it.key}}" }
        if (!value.isTrackable(key)) return null
        savedEarlier(index, key, value)?.let { return "\${$it}" }
        if (key == null || !(key.endsWith("Id") || key == "id")) return null
        val input = uniqueName(key, variableNames)
        inputs[input] = InputSpec(type = value.jsonType(), description = "recorded as ${value.render()}; likely different in another run", default = value)
        return "\${$input}"
    }

    /** The variable an earlier step saves [value] into, adding the save when needed; null when no earlier result holds it. */
    private fun savedEarlier(index: Int, key: String?, value: JsonPrimitive): String? {
        for (earlier in index - 1 downTo 0) {
            val path = findPath(calls[earlier].document, value) ?: continue
            val step = steps[earlier]
            step.keep = true
            return step.save.entries.firstOrNull { it.value == path }?.key
                ?: uniqueName(key ?: "value", variableNames).also { step.save[it] = path }
        }
        return null
    }
}

private class MutableStep(
    val id: String,
    val server: String,
    val tool: String,
    var args: JsonObject,
    val save: MutableMap<String, String>,
    val expect: List<Expectation>,
    var keep: Boolean,
)

// A recorded tool error is kept as an expected error, so the replay checks the same outcome.
private fun RecordedCall.toMutableStep(id: String) = MutableStep(
    id = id,
    server = server,
    tool = tool,
    args = arguments,
    save = linkedMapOf(),
    expect = (if (isError) listOf(Expectation(error = true)) else emptyList()) + expectations,
    keep = expectations.isNotEmpty(),
)

/** Replaces primitive leaves of [element] for which [replacement] returns a template. */
private fun rewrite(element: JsonElement, argumentKey: String?, replacement: (key: String?, value: JsonPrimitive) -> String?): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { (key, value) -> rewrite(value, key, replacement) })
    is JsonArray -> JsonArray(element.map { rewrite(it, argumentKey, replacement) })
    is JsonPrimitive -> replacement(argumentKey, element)?.let(::JsonPrimitive) ?: element
    is JsonNull -> JsonNull
}

/**
 * Whether [this] value, sent under [key], looks like something a server generated rather than a
 * constant the caller chose. Only such values are traced back to earlier results: a word like
 * `Settings` also appears in some earlier listing, and tying it to that listing's order would make
 * the replay fragile for nothing.
 */
private fun JsonPrimitive.isTrackable(key: String?): Boolean = when {
    content == "true" || content == "false" -> false
    !isString -> content.toDoubleOrNull()?.let { abs(it) >= 10 } == true
    key != null && IdLikeKey.containsMatchIn(key) -> content.isNotEmpty()
    else -> content.length >= MIN_TRACKED_STRING_LENGTH && content.any(Char::isDigit) && content.none(Char::isWhitespace)
}

/** A path to [value] inside [document], preferring identifying filters over list indices. */
internal fun findPath(document: JsonElement, value: JsonPrimitive): String? {
    fun search(node: JsonElement, path: String): String? = when (node) {
        is JsonPrimitive -> path.takeIf { jsonEquals(node, value) && node.isString == value.isString }
        is JsonObject -> node.entries.firstNotNullOfOrNull { (key, child) -> search(child, "$path${memberSegment(key)}") }
        is JsonArray -> node.withIndex().firstNotNullOfOrNull { (index, child) -> search(child, "$path${itemSegment(node, index)}") }
        is JsonNull -> null
    }
    return search(document, "$")
}

private fun memberSegment(key: String): String = if (key.all { it.isLetterOrDigit() || it == '_' }) ".$key" else "['${key.replace("'", "")}']"

/**
 * `[?(@.text == 'Settings')].first()` when the item has a field unique among its siblings, else
 * `[index]`. A liveness flag the item had (`isActive`, `connected`, …) joins the filter: a list of
 * sessions or devices keeps its dead entries under the same names, and the replay wants the live one.
 */
private fun itemSegment(array: JsonArray, index: Int): String {
    val item = array[index] as? JsonObject ?: return "[$index]"
    val flags = LivenessFlags.filter { (item[it] as? JsonPrimitive)?.content == "true" }
    val identity = IdentifyingFields.firstOrNull { field ->
        val value = (item[field] as? JsonPrimitive)?.takeIf { it.isString && "'" !in it.content } ?: return@firstOrNull false
        array.count { sibling -> sibling is JsonObject && sibling.sameAs(item, listOf(field) + flags) } == 1 && value.content.isNotEmpty()
    } ?: return "[$index]"
    val conditions = listOf("@.$identity == '${(item[identity] as JsonPrimitive).content}'") + flags.map { "@.$it == true" }
    return "[?(${conditions.joinToString(" && ")})].first()"
}

private fun JsonObject.sameAs(other: JsonObject, fields: List<String>): Boolean = fields.all { field -> (this[field] as? JsonPrimitive)?.content == (other[field] as? JsonPrimitive)?.content }

private fun uniqueName(base: String, taken: MutableSet<String>): String {
    val clean = base.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "value" }
    var candidate = clean
    var suffix = 2
    while (candidate in taken) candidate = "$clean${suffix++}"
    taken += candidate
    return candidate
}

private fun JsonElement.jsonType(): String = when {
    this is JsonPrimitive && isString -> "string"
    this is JsonPrimitive && (content == "true" || content == "false") -> "boolean"
    this is JsonPrimitive -> "number"
    this is JsonArray -> "array"
    else -> "object"
}

private val ReadOnlyVerbs = listOf("get", "list", "find", "describe", "read", "query", "search", "capture", "screenshot", "status", "dump", "measure")

/** A tool is read-only when its server says so, or when its name starts with a reading verb. */
fun isReadOnlyTool(name: String, readOnlyHint: Boolean?): Boolean {
    if (readOnlyHint != null) return readOnlyHint
    val verb = name.substringAfterLast('.').substringAfterLast("__").lowercase()
    return ReadOnlyVerbs.any(verb::startsWith)
}
