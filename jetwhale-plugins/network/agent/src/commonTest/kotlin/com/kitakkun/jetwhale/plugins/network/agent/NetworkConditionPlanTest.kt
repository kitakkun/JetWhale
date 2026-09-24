package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.plugins.network.protocol.findMatchingCondition
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class NetworkConditionPlanTest {
    @Test
    fun `the first enabled rule that matches applies and a disabled one is skipped`() {
        val rules = listOf(
            rule("disabled", matcher = MockMatcher(urlPattern = "/api/"), enabled = false, condition = LATENCY),
            rule("api", matcher = MockMatcher(urlPattern = "/api/"), enabled = true, condition = LATENCY),
            rule("everything", matcher = null, enabled = true, condition = LATENCY),
        )

        assertEquals("api", rules.findMatchingCondition("GET", "https://example.com/api/items")?.id)
        assertEquals("everything", rules.findMatchingCondition("GET", "https://example.com/images/a.png")?.id)
    }

    @Test
    fun `no rule means no condition`() {
        assertNull(emptyList<NetworkConditionRule>().findMatchingCondition("GET", "https://example.com/"))
    }

    @Test
    fun `jitter adds up to its bound on top of the latency`() {
        val condition = NetworkCondition(latencyMs = 100, jitterMs = 50)
        val latencies = (0 until 200).map { seed -> rule("r", matcher = null, enabled = true, condition = condition).plan(Random(seed)).latency }

        assertTrue(latencies.all { it in 100.milliseconds..150.milliseconds })
        assertTrue(latencies.distinct().size > 1)
    }

    @Test
    fun `a failure rate of one always fails and zero never does`() {
        val always = rule("always", matcher = null, enabled = true, condition = NetworkCondition(failureRate = 1.0, failure = InjectedFailure.TIMEOUT))
        val never = rule("never", matcher = null, enabled = true, condition = NetworkCondition(failureRate = 0.0))

        assertTrue((0 until 50).all { always.plan(Random(it)).failure == InjectedFailure.TIMEOUT })
        assertTrue((0 until 50).all { never.plan(Random(it)).failure == null })
    }

    @Test
    fun `offline overrides latency and failures`() {
        val plan = rule("offline", matcher = null, enabled = true, condition = NetworkCondition(latencyMs = 5_000, failureRate = 1.0, offline = true)).plan(Random(0))

        assertTrue(plan.offline)
        assertEquals(Duration.ZERO, plan.latency)
        assertNull(plan.failure)
    }

    @Test
    fun `the pacer holds bytes back until the rate allows them`() {
        val time = TestTimeSource()
        val pacer = BandwidthPacer(bytesPerSecond = 1_000, timeSource = time)

        assertEquals(500.milliseconds, pacer.delayBefore(500))
        time += 500.milliseconds
        assertEquals(500.milliseconds, pacer.delayBefore(500))
        time += 2_000.milliseconds
        assertEquals(Duration.ZERO, pacer.delayBefore(500))
    }

    @Test
    fun `the pacer chunk is a small slice of a second of data`() {
        assertEquals(5_000, BandwidthPacer(bytesPerSecond = 100_000, timeSource = TestTimeSource()).chunkSize)
        assertEquals(256, BandwidthPacer(bytesPerSecond = 1_000, timeSource = TestTimeSource()).chunkSize)
    }

    private fun rule(id: String, matcher: MockMatcher?, enabled: Boolean, condition: NetworkCondition) = NetworkConditionRule(id = id, name = id, enabled = enabled, matcher = matcher, condition = condition)
}

private val LATENCY = NetworkCondition(latencyMs = 1)
