package com.kitakkun.jetwhale.plugins.network.protocol

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
data class MockMatcher(
    val method: String? = null,
    val urlPattern: String,
    val matchType: MockMatchType = MockMatchType.CONTAINS,
)

/** The canned response returned when a [MockRule] matches. */
@Serializable
data class MockResponseSpec(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val delayMs: Long = 0,
)

/**
 * A single mock rule. Owned by the host UI and pushed to the agent via
 * [com.kitakkun.jetwhale.plugins.network.protocol.NetworkMethod.SetMockRules].
 */
@Serializable
data class MockRule(
    val id: String,
    val name: String = "",
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
