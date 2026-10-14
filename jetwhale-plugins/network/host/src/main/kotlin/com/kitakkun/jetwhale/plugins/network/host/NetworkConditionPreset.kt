package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import java.util.UUID

/**
 * Ready-made conditions for the networks an app most often has to survive. The rates follow the
 * usual browser devtools profiles, in bytes per second.
 */
internal enum class NetworkConditionPreset(val label: String, val condition: NetworkCondition) {
    Slow3G(
        label = "Slow 3G",
        condition = NetworkCondition(latencyMs = 400, jitterMs = 100, downloadBytesPerSecond = 50_000, uploadBytesPerSecond = 50_000),
    ),
    Fast3G(
        label = "Fast 3G",
        condition = NetworkCondition(latencyMs = 150, jitterMs = 50, downloadBytesPerSecond = 180_000, uploadBytesPerSecond = 84_000),
    ),
    Edge(
        label = "EDGE",
        condition = NetworkCondition(latencyMs = 800, jitterMs = 200, downloadBytesPerSecond = 30_000, uploadBytesPerSecond = 25_000),
    ),
    Flaky(
        label = "Flaky",
        condition = NetworkCondition(latencyMs = 200, jitterMs = 800, failureRate = 0.2, failure = InjectedFailure.CONNECTION_RESET),
    ),
    Offline(
        label = "Offline",
        condition = NetworkCondition(offline = true),
    ),
    ;

    /** A rule applying this preset to every request. */
    fun toRule(): NetworkConditionRule = NetworkConditionRule(
        id = UUID.randomUUID().toString(),
        name = label,
        enabled = true,
        matcher = null,
        condition = condition,
    )
}
