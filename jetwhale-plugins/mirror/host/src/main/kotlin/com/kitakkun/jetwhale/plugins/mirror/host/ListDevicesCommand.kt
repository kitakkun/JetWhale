package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListDevicesCommand(
    private val refreshDevices: suspend () -> List<MirrorDevice>,
    private val selectedDeviceId: () -> String?,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listDevices"
    override val description = "Lists connected Android emulators/devices and booted iOS simulators available for mirroring and input control. Use the returned deviceId with the other $TOOL_PREFIX tools; tools with deviceId omitted target the device selected in the mirror UI."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val devices = refreshDevices()
        val selected = selectedDeviceId()
        return buildJsonObject {
            put(
                "devices",
                JsonArray(
                    devices.map { device ->
                        buildJsonObject {
                            put("deviceId", device.id)
                            put("name", device.name)
                            put("platform", device.platform.name)
                            put("selected", device.id == selected)
                        }
                    },
                ),
            )
        }.toString()
    }
}
