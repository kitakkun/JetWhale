package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import com.kitakkun.jetwhale.plugins.network.protocol.findMatching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindMatchingTest {
    @Test
    fun `returns no mock when mocking is globally disabled`() {
        val rules = listOf(rule(pattern = "/users"))
        assertNull(rules.findMatching("GET", "https://api/users", enabled = false))
    }

    private fun rule(
        method: String? = null,
        pattern: String,
        type: MockMatchType = MockMatchType.CONTAINS,
        enabled: Boolean = true,
        status: Int = 200,
    ) = MockRule(
        id = pattern,
        enabled = enabled,
        matcher = MockMatcher(method = method, urlPattern = pattern, matchType = type),
        response = MockResponseSpec(statusCode = status),
    )

    @Test
    fun `returns the first enabled rule whose pattern the url contains`() {
        val rules = listOf(
            rule(pattern = "/users", status = 201),
            rule(pattern = "/users", status = 500),
        )
        assertEquals(201, rules.findMatching("GET", "https://api/users/1", enabled = true)?.statusCode)
    }

    @Test
    fun `skips rules that are disabled`() {
        val rules = listOf(
            rule(pattern = "/users", enabled = false, status = 201),
            rule(pattern = "/users", status = 503),
        )
        assertEquals(503, rules.findMatching("GET", "https://api/users", enabled = true)?.statusCode)
    }

    @Test
    fun `matches the method case-insensitively when a rule names one`() {
        val rules = listOf(rule(method = "POST", pattern = "/users"))
        assertNull(rules.findMatching("GET", "https://api/users", enabled = true))
        assertEquals(200, rules.findMatching("post", "https://api/users", enabled = true)?.statusCode)
    }

    @Test
    fun `supports exact and regex match types`() {
        val exact = listOf(rule(pattern = "https://api/users", type = MockMatchType.EXACT))
        assertNull(exact.findMatching("GET", "https://api/users/1", enabled = true))
        assertEquals(200, exact.findMatching("GET", "https://api/users", enabled = true)?.statusCode)

        val regex = listOf(rule(pattern = ".*/users/\\d+$", type = MockMatchType.REGEX))
        assertEquals(200, regex.findMatching("GET", "https://api/users/42", enabled = true)?.statusCode)
        assertNull(regex.findMatching("GET", "https://api/users", enabled = true))
    }
}
