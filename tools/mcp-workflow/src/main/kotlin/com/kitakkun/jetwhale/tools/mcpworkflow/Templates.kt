package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val Placeholder = Regex("""\$\{([^}]+)}""")

class TemplateException(message: String) : Exception(message)

/**
 * Replaces `${name}` placeholders in every string of [element].
 *
 * A string that is exactly one placeholder becomes the variable's JSON value, keeping its type — an
 * id saved as a number stays a number. A placeholder inside longer text is spliced in as text.
 * `${name.a[0]}` reads into a saved object with a [JsonPath]; `${env.NAME}` reads the environment.
 */
fun renderTemplate(element: JsonElement, variables: Map<String, JsonElement>, environment: Map<String, String>): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { (_, value) -> renderTemplate(value, variables, environment) })
    is JsonArray -> JsonArray(element.map { renderTemplate(it, variables, environment) })
    is JsonPrimitive -> if (element.isString) renderString(element.content, variables, environment) else element
    is JsonNull -> JsonNull
}

private fun renderString(text: String, variables: Map<String, JsonElement>, environment: Map<String, String>): JsonElement {
    val whole = Placeholder.matchEntire(text)
    if (whole != null) return resolve(whole.groupValues[1].trim(), variables, environment)
    return JsonPrimitive(
        Placeholder.replace(text) { match ->
            when (val value = resolve(match.groupValues[1].trim(), variables, environment)) {
                is JsonPrimitive -> value.content
                else -> value.toString()
            }
        },
    )
}

private fun resolve(reference: String, variables: Map<String, JsonElement>, environment: Map<String, String>): JsonElement {
    if (reference.startsWith("env.")) {
        val name = reference.removePrefix("env.")
        return JsonPrimitive(environment[name] ?: throw TemplateException("environment variable $name is not set"))
    }
    val name = reference.takeWhile { it != '.' && it != '[' }
    val value = variables[name] ?: throw TemplateException("\${$reference} refers to '$name', which is not defined at this point")
    val rest = reference.removePrefix(name)
    if (rest.isEmpty()) return value
    return JsonPath.parse("$$rest").select(value) ?: throw TemplateException("\${$reference} matched nothing in '$name'")
}

/** The variable names [element] refers to, for checking a workflow before it runs. */
fun templateReferences(element: JsonElement): Set<String> = when (element) {
    is JsonObject -> element.values.flatMapTo(mutableSetOf(), ::templateReferences)

    is JsonArray -> element.flatMapTo(mutableSetOf(), ::templateReferences)

    is JsonPrimitive -> if (element.isString) {
        Placeholder.findAll(element.content)
            .map { it.groupValues[1].trim() }
            .filterNot { it.startsWith("env.") }
            .mapTo(mutableSetOf()) { reference -> reference.takeWhile { it != '.' && it != '[' } }
    } else {
        emptySet()
    }

    is JsonNull -> emptySet()
}
