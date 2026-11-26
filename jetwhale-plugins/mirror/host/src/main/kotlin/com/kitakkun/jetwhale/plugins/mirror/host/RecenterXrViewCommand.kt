package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.delay

@OptIn(ExperimentalJetWhaleApi::class)
internal class RecenterXrViewCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.recenterXrView"
    override val description =
        "Puts an Android XR emulator's view back where it started, facing forward, after $TOOL_PREFIX.turnXrView or a turn in the mirror. Only an Android XR emulator has such a view: kind \"XR emulator\" in $TOOL_PREFIX.listDevices."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val xrView = xrViewControllerOf(deviceOperation { mirror.resolve(arguments[deviceId]) })
        deviceOperation(xrView::recenterView)
        delay(XR_VIEW_SETTLE_TIME)
        return okJson()
    }
}
