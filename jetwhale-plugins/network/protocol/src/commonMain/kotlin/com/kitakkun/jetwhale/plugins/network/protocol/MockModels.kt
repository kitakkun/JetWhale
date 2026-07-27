package com.kitakkun.jetwhale.plugins.network.protocol

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.Serializable

/** How a [MockMatcher.urlPattern] is compared against a request URL. */
@Serializable
enum class MockMatchType {
    CONTAINS,
    EXACT,
    REGEX,
}

/**
 * Matches a request. [method] is compared case-insensitively; null/blank means "any method".
 */
@Serializable
@McpDescription("Which requests the rule applies to.")
data class MockMatcher(
    @McpDescription("HTTP method to match (case-insensitive). Matches any method if omitted.")
    val method: String? = null,
    @McpDescription("URL pattern to match, interpreted per matchType.")
    val urlPattern: String,
    @McpDescription("How urlPattern is compared against the request URL. Defaults to CONTAINS.")
    val matchType: MockMatchType = MockMatchType.CONTAINS,
)

/** The canned response returned when a [MockRule] matches. */
@Serializable
@McpDescription("The canned response returned instead of hitting the network.")
data class MockResponseSpec(
    @McpDescription("Status code of the mocked response. Defaults to 200.")
    val statusCode: Int = 200,
    @McpDescription("Response headers, e.g. {\"Content-Type\":\"application/json\"}. Defaults to none.")
    val headers: Map<String, String> = emptyMap(),
    @McpDescription("Body of the mocked response. Defaults to empty.")
    val body: String = "",
    @McpDescription("Artificial delay before the mocked response is delivered, in milliseconds. Defaults to 0.")
    val delayMs: Long = 0,
)

/**
 * A single mock rule. Owned by the host UI and pushed to the agent via
 * [com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethod.SetMockRules].
 */
@Serializable
data class MockRule(
    @McpDescription("Stable identifier of the rule. Reuse the id of an existing rule to edit it; use a fresh UUID for a new one.")
    val id: String,
    @McpDescription("Human-readable rule name shown in the UI.")
    val name: String = "",
    @McpDescription("Whether the rule takes effect. Defaults to true.")
    val enabled: Boolean = true,
    val matcher: MockMatcher,
    val response: MockResponseSpec,
)

/**
 * Returns the [MockResponseSpec] of the first enabled rule that matches [method]/[url], honoring
 * the global [enabled] flag. Shared so every adapter applies mocks identically.
 */
fun List<MockRule>.findMatching(method: String, url: String, enabled: Boolean): MockResponseSpec? {
    if (!enabled) return null
    return firstOrNull { rule ->
        rule.enabled &&
            rule.matcher.matches(method, url)
    }?.response
}

private fun MockMatcher.matches(method: String, url: String): Boolean {
    val methodMatches = this.method.isNullOrBlank() || this.method.equals(method, ignoreCase = true)
    if (!methodMatches) return false
    return when (matchType) {
        MockMatchType.CONTAINS -> url.contains(urlPattern)
        MockMatchType.EXACT -> url == urlPattern
        MockMatchType.REGEX -> runCatching { Regex(urlPattern).containsMatchIn(url) }.getOrDefault(false)
    }
}
