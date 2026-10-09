package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SoilEntryFactsTest {
    @Test
    fun `a timestamp Soil never set reads as what zero means for that field`() {
        assertEquals("never", describeEpochSeconds(0, agentNowEpochSeconds = 1_000, whenZero = "never"))
    }

    @Test
    fun `an infinite staleTime reads as never`() {
        assertEquals("never", describeEpochSeconds(Long.MAX_VALUE, agentNowEpochSeconds = 1_000, whenZero = "unused"))
    }

    @Test
    fun `timestamps read relative to the app clock`() {
        assertEquals("1m 15s ago", describeEpochSeconds(925, agentNowEpochSeconds = 1_000, whenZero = "unused"))
        assertEquals("in 2h 0m", describeEpochSeconds(8_200, agentNowEpochSeconds = 1_000, whenZero = "unused"))
    }

    @Test
    fun `an inactive stale query observed by nobody is labelled as such`() {
        val entry = queryEntry(handle = "query-1", namespace = "users", location = SoilEntryLocation.INACTIVE, staleAt = 900, isObserved = false)
        val validating = entry.copy(state = (entry.state as SoilEntryState.Query).copy(fetchStatus = SoilFetchStatus.Fetching(isValidating = true), isInvalidated = true))

        val badges = badgesOf(ListedSoilEntry(validating, isGone = false), agentNowEpochSeconds = HOST_NOW)

        assertEquals(
            listOf(
                SoilEntryBadge("Success", JwTone.Success, isRoutine = true),
                SoilEntryBadge("Validating", JwTone.Accent, isRoutine = false),
                SoilEntryBadge("Stale", JwTone.Warning, isRoutine = false),
                SoilEntryBadge("Invalidated", JwTone.Warning, isRoutine = false),
                SoilEntryBadge("Inactive", JwTone.Neutral, isRoutine = false),
            ),
            badges,
        )
    }

    @Test
    fun `an active entry no screen observes is labelled unobserved and an observed one routinely`() {
        val unobserved = badgesOf(ListedSoilEntry(queryEntry(handle = "query-1", namespace = "users", isObserved = false), isGone = false), agentNowEpochSeconds = 900)
        val observed = badgesOf(ListedSoilEntry(queryEntry(handle = "query-1", namespace = "users"), isGone = false), agentNowEpochSeconds = 900)

        assertEquals(SoilEntryBadge("Unobserved", JwTone.Neutral, isRoutine = false), unobserved.last())
        assertEquals(SoilEntryBadge("Observed", JwTone.Info, isRoutine = true), observed.last())
    }

    @Test
    fun `durations read in milliseconds under a second and in tenths of a second under a minute`() {
        assertEquals(listOf("340ms", "2.4s", "1m 5s"), listOf(340L, 2_450L, 65_000L).map(::describeDurationMillis))
    }

    @Test
    fun `a JSON value opens at its root and further only where expanded`() {
        val json = Json.parseToJsonElement("""{"user":{"name":"Ada","roles":["admin"]},"count":2}""")

        assertEquals(
            listOf(
                JsonTreeLine(path = "", depth = 0, text = "{2}", isExpandable = true),
                JsonTreeLine(path = "/user", depth = 1, text = "user: {2}", isExpandable = true),
                JsonTreeLine(path = "/user/name", depth = 2, text = "name: \"Ada\"", isExpandable = false),
                JsonTreeLine(path = "/user/roles", depth = 2, text = "roles: [1]", isExpandable = true),
                JsonTreeLine(path = "/count", depth = 1, text = "count: 2", isExpandable = false),
            ),
            flattenJsonTree(json, expandedPaths = setOf(JSON_ROOT_PATH, "/user")),
        )
    }

    @Test
    fun `keys with dots or slashes get paths of their own`() {
        val json = Json.parseToJsonElement("""{"a/b":{"c":1},"a":{"b":{"c":2}},"a.b":{"c":3}}""")

        val paths = flattenJsonTree(json, expandedPaths = setOf(JSON_ROOT_PATH, "/a", "/a~1b")).map(JsonTreeLine::path)

        assertEquals(listOf("", "/a~1b", "/a~1b/c", "/a", "/a/b", "/a.b"), paths)
    }
}
