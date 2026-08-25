package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.put
import java.io.File

@OptIn(ExperimentalJetWhaleApi::class)
internal class PushFileCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.pushFile"
    override val description =
        "Copies a file from this machine onto the device — a fixture, a database, a config. " +
            "Destructive: an existing file at devicePath is overwritten."

    private val hostPath by string("Absolute path of the source file on the machine running the debug tool.")
    private val devicePath by string("Destination path on the device, e.g. /sdcard/Download/fixture.json.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val source = File(arguments[hostPath])
        if (!source.isAbsolute) throw JetWhaleMcpArgumentException("invalid hostPath: ${source.path} (expected an absolute path)")
        if (!source.exists()) throw JetWhaleMcpArgumentException("invalid hostPath: ${source.path} (no such file)")
        val devicePath = requireDevicePath(arguments[devicePath])

        val result = target.adb("push", source.absolutePath, devicePath, timeout = AdbTimeouts.TRANSFER)
        target.requireSuccess(result, "adb push failed")?.let { return it }
        return target.successResult {
            put("hostPath", source.absolutePath)
            put("devicePath", devicePath)
            put("output", result.output.trim())
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class PullFileCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.pullFile"
    override val description = "Copies a file off the device onto this machine — a log, a database, a crash dump. An existing file at hostPath is overwritten."

    private val devicePath by string("Path of the source file on the device, e.g. /sdcard/Download/report.txt.")
    private val hostPath by string("Absolute destination path on the machine running the debug tool. Its parent directory must exist.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val devicePath = requireDevicePath(arguments[devicePath])
        val destination = File(arguments[hostPath])
        if (!destination.isAbsolute) throw JetWhaleMcpArgumentException("invalid hostPath: ${destination.path} (expected an absolute path)")
        if (destination.parentFile?.isDirectory != true) throw JetWhaleMcpArgumentException("invalid hostPath: ${destination.path} (its parent directory does not exist)")

        val result = target.adb("pull", devicePath, destination.absolutePath, timeout = AdbTimeouts.TRANSFER)
        target.requireSuccess(result, "adb pull failed")?.let { return it }
        return target.successResult {
            put("devicePath", devicePath)
            put("hostPath", destination.absolutePath)
            put("bytes", destination.length())
            put("output", result.output.trim())
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class ReversePortCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.reversePort"
    override val description =
        "Maps a port on the device to a port on this machine, so an app can reach a server running " +
            "here. Needed when the debug tool listens on a non-default port, or for any other local service."

    private val devicePort by int("Port the app connects to on the device.")
    private val hostPort by int("Port the traffic is forwarded to on the machine running the debug tool.")
    private val remove by booleanOrNull("Remove the mapping for devicePort instead of creating one. Defaults to false; hostPort is ignored when it is true.")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val devicePort = requirePort("devicePort", arguments[devicePort])
        val hostPort = requirePort("hostPort", arguments[hostPort])
        val remove = arguments[remove] ?: false

        val result = if (remove) {
            target.adb("reverse", "--remove", "tcp:$devicePort", timeout = AdbTimeouts.QUICK)
        } else {
            target.adb("reverse", "tcp:$devicePort", "tcp:$hostPort", timeout = AdbTimeouts.QUICK)
        }
        target.requireSuccess(result, if (remove) "adb reverse --remove failed" else "adb reverse failed")?.let { return it }
        return target.successResult {
            put("devicePort", devicePort)
            put("hostPort", if (remove) null else hostPort)
            put("removed", remove)
        }
    }
}

/** adb resolves a relative device path against whatever `adb shell`'s working directory happens to be, which is not something a QA run should depend on. */
@OptIn(ExperimentalJetWhaleApi::class)
private fun requireDevicePath(value: String): String {
    if (!value.startsWith("/")) throw JetWhaleMcpArgumentException("invalid device path: $value (expected an absolute path such as /sdcard/Download/file.json)")
    return value
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun requirePort(name: String, value: Int): Int {
    if (value !in 1..65535) throw JetWhaleMcpArgumentException("invalid $name: $value (expected a port between 1 and 65535)")
    return value
}
