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
        "Lists the Android emulators and devices, booted iOS simulators and USB-connected iOS devices this machine can mirror. Each has a deviceId for the other $TOOL_PREFIX tools, its kind, and what it supports. A device that takes no input (taps, swipes, buttons and text) says why in \"inputUnavailableReason\": a physical iOS device needs Xcode, iproxy and a development team set in the mirror, and its input is experimental. A physical iOS device gives screenshots and recordings only when ffmpeg is installed and macOS allows the host Camera access, since both come from the capture of its screen. " +
            "An Android device also reports \"screenOn\" and \"locked\"; a screen that is off shows as black, and $TOOL_PREFIX.setScreen turns it on. " +
            "An Android XR emulator lists as kind \"XR emulator\" and reports \"passthrough\"; $TOOL_PREFIX.turnXrView, $TOOL_PREFIX.recenterXrView and $TOOL_PREFIX.setXrPassthrough move its view. An Android XR headset lists as kind \"XR device\" and takes no input."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val devices = mirror.refresh()
        val selected = mirror.selectedId
        val screenPowers = devices.associate { it.id to readableScreenPower(it.controller) }
        val xrPassthroughs = devices.associate { it.id to readableXrPassthrough(it.controller) }
        return buildJsonObject {
            putJsonArray("devices") {
                devices.forEach { device ->
                    val capabilities = device.controller.capabilities
                    addJsonObject {
                        put("deviceId", device.id)
                        put("name", device.name)
                        put("platform", device.kind.platform.label)
                        put("kind", device.kind.label)
                        device.listing.osVersion?.let { put("osVersion", it) }
                        put("selected", device.id == selected)
                        put("input", capabilities.inputRefusal == null)
                        capabilities.inputRefusal?.let { put("inputUnavailableReason", it) }
                        (device.controller as? XcTestRunnerDeviceController)?.runnerKeptAliveUntil()?.let { put("runnerKeptAliveUntil", it.toString()) }
                        put("recording", capabilities.recording)
                        putJsonArray("buttons") { capabilities.buttons.forEach { add(it.name) } }
                        screenPowers.getValue(device.id)?.let { power ->
                            put("screenOn", power.awake)
                            put("locked", power.locked)
                        }
                        xrPassthroughs.getValue(device.id)?.let { put("passthrough", it) }
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

/** Whether an XR emulator shows the room through its scene; null for any other device, and when it cannot be read. */
private suspend fun readableXrPassthrough(controller: DeviceController): Boolean? {
    if (controller !is XrViewController || controller.capabilities.inputRefusal != null) return null
    return try {
        controller.isPassthroughOn()
    } catch (_: DeviceControlException) {
        null
    }
}
