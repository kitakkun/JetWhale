package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes

/** The longest lease one call takes; the runner itself grants no more. */
private const val MAX_KEEP_ALIVE_MINUTES = 120

@OptIn(ExperimentalJetWhaleApi::class)
internal class KeepRunnerAliveCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.keepRunnerAlive"
    override val description =
        "Keeps an iOS device's XCTest runner, which sends its input, from stopping after five idle minutes, for a long session with pauses between inputs. " +
            "The lease lasts the given minutes, at most $MAX_KEEP_ALIVE_MINUTES, and ends on its own; call again to renew it, or with 0 to end it. " +
            "Returns at once, with the time the lease ends as runnerKeptAliveUntil, which $TOOL_PREFIX.listDevices also shows."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val minutes by int("How long to keep the runner alive, from 0 to $MAX_KEEP_ALIVE_MINUTES minutes; 0 ends the lease.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val leaseMinutes = arguments[minutes]
        if (leaseMinutes !in 0..MAX_KEEP_ALIVE_MINUTES) throw JetWhaleMcpArgumentException("minutes must be from 0 to $MAX_KEEP_ALIVE_MINUTES (got $leaseMinutes)")
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        val runnerDriven = device.controller as? XcTestRunnerDriven ?: throw JetWhaleMcpArgumentException("${device.name} is not driven through an XCTest runner; only iOS simulators and devices are")
        device.controller.capabilities.inputRefusal?.let { throw JetWhaleMcpArgumentException(it) }
        val until = deviceOperation { runnerDriven.keepRunnerAlive(leaseMinutes.minutes) }
        return buildJsonObject { put("runnerKeptAliveUntil", until.toString()) }.toString()
    }
}
