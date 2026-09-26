package com.kitakkun.jetwhale.plugins.mirror.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListDevicesCommand(
    private val mirror: MirrorDevices,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listDevices"
    override val description =
        "Lists the Android emulators and devices, booted iOS simulators and USB-connected iOS devices this machine can mirror. Each has a deviceId for the other $TOOL_PREFIX tools, its kind, and what it supports: a physical iOS device is view-only (screenshots, no input or recording). " +
            "An Android device also reports \"screenOn\" and \"locked\"; a screen that is off shows as black, and $TOOL_PREFIX.setScreen turns it on."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val devices = mirror.refresh()
        val selected = mirror.selectedId
        val screenPowers = devices.associate { it.id to readableScreenPower(it.controller) }
        return buildJsonObject {
            putJsonArray("devices") {
                devices.forEach { device ->
                    addJsonObject {
                        put("deviceId", device.id)
                        put("name", device.name)
                        put("platform", device.kind.platform.label)
                        put("kind", device.kind.label)
                        device.listing.osVersion?.let { put("osVersion", it) }
                        put("selected", device.id == selected)
                        put("input", device.controller.capabilities.input)
                        put("recording", device.controller.capabilities.recording)
                        putJsonArray("buttons") { device.controller.capabilities.buttons.forEach { add(it.name) } }
                        screenPowers.getValue(device.id)?.let { power ->
                            put("screenOn", power.awake)
                            put("locked", power.locked)
                        }
                    }
                }
            }
        }.toString()
    }
}

/** The screen state of a device that has one to read; an unreadable state is left out rather than guessed. */
private suspend fun readableScreenPower(controller: DeviceController): ScreenPower? {
    if (!controller.capabilities.screenPower) return null
    return try {
        controller.screenPower()
    } catch (_: DeviceControlException) {
        null
    }
}
