package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class SetScreenCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.setScreen"
    override val description =
        "Turns an Android device's screen on or off; a screen that is off shows as black in screenshots and the mirror. Turning it on also dismisses a lock screen " +
            "that has no PIN, pattern or password. Answers the screen state afterwards; \"locked\": true means the device still needs unlocking. iOS devices are not supported."

    private val deviceId by stringOrNull(DEVICE_ID_DESCRIPTION)
    private val on by boolean("true to turn the screen on, false to turn it off.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val device = deviceOperation { mirror.resolve(arguments[deviceId]) }
        val power = deviceOperation {
            if (arguments[on]) device.controller.wake() else device.controller.sleep()
            device.controller.screenPower()
        }
        return buildJsonObject {
            put("screenOn", power.awake)
            put("locked", power.locked)
        }.toString()
    }
}
