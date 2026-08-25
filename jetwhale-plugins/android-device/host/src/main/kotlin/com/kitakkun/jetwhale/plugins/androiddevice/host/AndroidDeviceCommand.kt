package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.InputStream
import kotlin.time.Duration

/** The tool-name prefix every command shares; it is this plugin's pluginId. */
internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.androiddevice"

internal const val SERIAL_DESCRIPTION =
    "adb serial of the device to act on, as listed by listDevices. Omit it when exactly one device " +
        "is connected; with several connected, omitting it is an error rather than a guess."

/**
 * A tool that acts on one device. It resolves the target before the tool body runs, so no command
 * can reach a device it did not name, and it records every adb argument vector it issues so the
 * result can say exactly what ran.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal abstract class AndroidDeviceCommand(private val adb: JetWhaleAdb) : JetWhaleMcpCommand() {
    // Declared here so it is the first parameter of every tool; base-class property initializers
    // run before the subclass declares its own.
    private val serial by stringOrNull(SERIAL_DESCRIPTION)

    final override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val run = AdbRun(adb)
        return try {
            val device = resolveDevice(run, arguments[serial])
            executeOnDevice(arguments, DeviceTarget(device, run))
        } catch (e: JetWhaleAdbUnavailableException) {
            adbUnavailableResult(e)
        }
    }

    protected abstract suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult
}

/**
 * The resolved device plus the recorder of what has been run against it. Every adb call a tool
 * makes goes through here, so the `-s <serial>` targeting cannot be forgotten.
 */
internal class DeviceTarget(
    val device: AdbDevice,
    private val run: AdbRun,
) {
    val serial: String get() = device.serial

    /** Runs `adb -s <serial> <args>`. */
    suspend fun adb(vararg args: String, timeout: Duration): JetWhaleAdbResult = run.exec("-s", serial, *args, timeout = timeout)

    /** Runs `adb -s <serial> shell <args>`; the arguments reach the device joined by spaces. */
    suspend fun shell(vararg args: String, timeout: Duration): JetWhaleAdbResult = adb("shell", *args, timeout = timeout)

    /** Runs `adb -s <serial> <args>` and hands the caller raw stdout, for binary or unbounded output. */
    suspend fun <T> stream(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T = run.stream("-s", serial, *args, timeout = timeout, consume = consume)

    /** The adb argument vectors run so far, oldest first. */
    val invocations: List<List<String>> get() = run.invocations
}

/**
 * Builds a tool result: the device it acted on, whatever the tool has to say, and the adb argument
 * vectors that produced it. `adb` comes last so the interesting part of the payload is read first.
 */
internal fun DeviceTarget.resultJson(build: JsonObjectBuilder.() -> Unit): String = buildJsonObject {
    put("serial", serial)
    build()
    put("adb", invocations.toJson())
}.toString()

/** A tool result for an adb command the device refused: the tool ran, the device said no. */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun DeviceTarget.failureResult(result: JetWhaleAdbResult, error: String): JetWhaleMcpResult = JetWhaleMcpResult.text(
    resultJson {
        put("ok", false)
        put("error", error)
        put("exitCode", result.exitCode)
        put("output", result.output.trim())
    },
)

/** A tool result for an adb command that did what was asked. */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun DeviceTarget.successResult(build: JsonObjectBuilder.() -> Unit = {}): JetWhaleMcpResult = JetWhaleMcpResult.text(
    resultJson {
        put("ok", true)
        build()
    },
)

/** Fails the call unless the adb command succeeded, so a tool body never reads output of a failed command. */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun DeviceTarget.requireSuccess(result: JetWhaleAdbResult, error: String): JetWhaleMcpResult? = if (result.exitCode == 0) null else failureResult(result, error)

internal fun List<List<String>>.toJson(): JsonArray = JsonArray(map { invocation -> JsonArray(invocation.map(::JsonPrimitive)) })

/**
 * No amount of retrying brings a missing SDK back, so this is reported as plainly as possible
 * rather than as a device-level failure.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun adbUnavailableResult(e: JetWhaleAdbUnavailableException): JetWhaleMcpResult = JetWhaleMcpResult.text(
    buildJsonObject {
        put("ok", false)
        put("error", "adb is not available on this machine: ${e.message}")
    }.toString(),
)

/**
 * Picks the device a tool acts on.
 *
 * A named serial must be connected and usable; an omitted one resolves only when there is exactly
 * one candidate, because silently picking one of several is the mistake this plugin exists to
 * prevent.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal suspend fun resolveDevice(run: AdbRun, serial: String?): AdbDevice {
    val result = run.exec("devices", "-l", timeout = AdbTimeouts.QUICK)
    if (result.exitCode != 0) {
        throw JetWhaleMcpArgumentException("`adb devices -l` failed with exit code ${result.exitCode}: ${result.output.trim()}")
    }
    val devices = parseAdbDevices(result.output)

    if (serial != null) {
        val match = devices.firstOrNull { it.serial == serial }
            ?: throw JetWhaleMcpArgumentException("unknown serial: $serial (${describe(devices)})")
        if (!match.isUsable) {
            throw JetWhaleMcpArgumentException("device $serial is in state '${match.state}', not '${AdbDevice.USABLE_STATE}'")
        }
        return match
    }

    val usable = devices.filter { it.isUsable }
    return when (usable.size) {
        1 -> usable.single()

        0 -> throw JetWhaleMcpArgumentException("no device is connected (${describe(devices)})")

        else -> throw JetWhaleMcpArgumentException(
            "several devices are connected, so serial is required: ${usable.joinToString(", ") { it.serial }}",
        )
    }
}

private fun describe(devices: List<AdbDevice>): String = if (devices.isEmpty()) {
    "`adb devices -l` lists no devices at all"
} else {
    "known devices: " + devices.joinToString(", ") { "${it.serial} (${it.state})" }
}
