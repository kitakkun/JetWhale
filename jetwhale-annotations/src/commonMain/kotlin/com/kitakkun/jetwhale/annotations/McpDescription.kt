package com.kitakkun.jetwhale.annotations

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Documents a `@Serializable` property or class for AI agents. JetWhale copies [value] into the
 * `description` of the generated JSON Schema when the type is used as an MCP tool parameter, so
 * the documentation lives next to the field it describes instead of being restated in the tool's
 * own description.
 *
 * ```kotlin
 * @Serializable
 * data class MockMatcher(
 *     @McpDescription("HTTP method to match (case-insensitive). Matches any method if omitted.")
 *     val method: String? = null,
 * )
 * ```
 *
 * [AnnotationTarget.VALUE_PARAMETER] is deliberately not an allowed target: the Kotlin compiler
 * prioritizes it over [AnnotationTarget.PROPERTY], and a serial info annotation resolved to the
 * value parameter is not preserved in the `SerialDescriptor`. Restricting the targets makes the
 * annotation land on the property even when written on a constructor parameter.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class McpDescription(val value: String)
