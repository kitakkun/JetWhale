package com.kitakkun.jetwhale.plugins.deeplinks.host

import java.net.URLEncoder

private val PLACEHOLDER = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\}""")

/** The `{name}` placeholders of [template], each once, in the order they first appear. */
internal fun placeholdersOf(template: String): List<String> = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.distinct().toList()

/**
 * [template] with each placeholder replaced by its value from [values], percent-encoded so a value
 * cannot add path segments or query parameters of its own.
 *
 * @throws IllegalArgumentException when a placeholder has no value or an empty one.
 */
internal fun fillTemplate(template: String, values: Map<String, String>): String = PLACEHOLDER.replace(template) { match ->
    val name = match.groupValues[1]
    val value = values[name]
    require(!value.isNullOrEmpty()) { "'$name' needs a value" }
    // URLEncoder encodes for forms, where a space is '+'; in a path it has to be %20.
    URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
