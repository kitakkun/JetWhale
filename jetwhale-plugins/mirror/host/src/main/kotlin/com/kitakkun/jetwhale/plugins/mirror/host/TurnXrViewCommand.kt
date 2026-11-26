package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.delay

/** Past this the view would turn over the top; a larger turn up or down is more likely a mistake. */
private const val MAX_UP_DEGREES = 90

private const val MAX_RIGHT_DEGREES = 180

@OptIn(ExperimentalJetWhaleApi::class)
internal class TurnXrViewCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.turnXrView"
    override val description =
        "Turns an Android XR emulator's view from where it faces now, as turning one's head does; the screen, screenshots and taps follow it. Use it to bring an app or anything off to the side into view before tapping it, and " +
            "$TOOL_PREFIX.recenterXrView to face forward again. Only an Android XR emulator has such a view: kind \"XR emulator\" in $TOOL_PREFIX.listDevices."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val rightDegrees by intOrNull("Degrees to turn right, from -$MAX_RIGHT_DEGREES to $MAX_RIGHT_DEGREES; negative turns left. Defaults to 0.")
    private val upDegrees by intOrNull("Degrees to turn up, from -$MAX_UP_DEGREES to $MAX_UP_DEGREES; negative turns down. Defaults to 0.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val right = arguments[rightDegrees] ?: 0
        val up = arguments[upDegrees] ?: 0
        if (right !in -MAX_RIGHT_DEGREES..MAX_RIGHT_DEGREES) throw JetWhaleMcpArgumentException("rightDegrees must be from -$MAX_RIGHT_DEGREES to $MAX_RIGHT_DEGREES (got $right)")
        if (up !in -MAX_UP_DEGREES..MAX_UP_DEGREES) throw JetWhaleMcpArgumentException("upDegrees must be from -$MAX_UP_DEGREES to $MAX_UP_DEGREES (got $up)")
        val xrView = xrViewControllerOf(deviceOperation { mirror.resolve(arguments[deviceId]) })
        deviceOperation { xrView.turnView(rightDegrees = right.toDouble(), upDegrees = up.toDouble()) }
        delay(XR_VIEW_SETTLE_TIME)
        return okJson()
    }
}
