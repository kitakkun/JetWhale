package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

/**
 * Problems found without running anything: unknown servers, variables used before any step saves
 * them, paths and durations that do not parse, duplicate step ids.
 */
fun validate(workflow: Workflow, servers: Set<String>): List<String> {
    val problems = mutableListOf<String>()
    val defined = (workflow.inputs.keys + workflow.vars.keys).toMutableSet()
    val ids = mutableSetOf<String>()
    workflow.vars.values.forEach { template ->
        (templateReferences(template) - workflow.inputs.keys - workflow.vars.keys).forEach { problems += "vars refer to '$it', which is not an input or var" }
    }
    workflow.steps.map { it.withDefaults(workflow.defaults) }.forEachIndexed { index, step ->
        val where = "step ${index + 1} (${stepLabel(index, step)})"
        step.id?.let { if (!ids.add(it)) problems += "$where: duplicate id '$it'" }
        when {
            step.server != null && step.server !in servers -> problems += "$where: no server named '${step.server}'; configured: ${servers.joinToString().ifEmpty { "none" }}"
            step.server == null && servers.size != 1 -> problems += "$where: names no `server`, and there are ${servers.size} servers to choose from"
        }
        (templateReferences(step.args) - defined).forEach { problems += "$where: refers to '$it' before anything defines it" }
        step.save.forEach { (name, path) -> checkPath(path, defined)?.let { problems += "$where: save '$name': $it" } }
        step.expect.forEach { expectation ->
            checkPath(expectation.path, defined)?.let { problems += "$where: expect: $it" }
            val templates = listOfNotNull(expectation.equals, expectation.notEquals, expectation.contains, expectation.matches?.let(::JsonPrimitive))
            (templates.flatMap(::templateReferences).toSet() - defined).forEach { problems += "$where: expect refers to '$it' before anything defines it" }
            expectation.matches?.let { pattern -> runCatching { Regex(pattern) }.onFailure { problems += "$where: expect: /$pattern/ is not a regex" } }
        }
        listOfNotNull(step.timeout, step.wait?.timeout, step.wait?.interval).forEach { text ->
            if (Duration.parseOrNull(text) == null) problems += "$where: '$text' is not a duration (e.g. 500ms, 10s)"
        }
        if (step.retries < 0) problems += "$where: retries cannot be negative"
        defined += step.save.keys
    }
    workflow.outputs.values.forEach { template ->
        (templateReferences(template) - defined).forEach { problems += "outputs refer to '$it', which no input, var or step defines" }
    }
    return problems
}

/** Why [path] is unusable, or null. A templated path can only be parsed once it is rendered, so only its references are checked. */
private fun checkPath(path: String, defined: Set<String>): String? {
    val references = templateReferences(JsonPrimitive(path))
    if (references.isNotEmpty()) return (references - defined).firstOrNull()?.let { "$path refers to '$it' before anything defines it" }
    return try {
        JsonPath.parse(path)
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }
}
