package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.util.findAdbPath
import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

// How long to wait before re-attaching `adb track-devices` after the tracking stream ends or errors
// (e.g. another tool ran `adb kill-server`, or adb restarted because of a client/server version mismatch).
private const val RECONNECT_DELAY_MS = 2_000L

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultADBAutoWiringService : ADBAutoWiringService {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val wiredDevices = ConcurrentSet<String>()

    // Every port currently subject to auto wiring (ws and wss are wired independently). A single
    // device-tracking job serves all of them.
    private val wiredPorts = ConcurrentSet<Int>()
    private var wiringJob: Job? = null
    private val adbPath: String by lazy { findAdbPath() }

    override fun startAutoWiring(port: Int) {
        if (!wiredPorts.add(port)) return

        // Devices that connected before this port was registered still need the new forwarding.
        wiredDevices.forEach { serial -> wire(serial, port) }

        if (wiringJob != null) return

        wiringJob = coroutineScope.launch {
            // `adb track-devices` can end at any time (adb server restart/crash, USB hiccup, version
            // mismatch). When it does, the flow completes; without this loop the service would silently
            // stop wiring until the host is restarted. Re-attach with a small backoff instead.
            while (isActive) {
                try {
                    deviceEventFlow().collect { event ->
                        when (event) {
                            is DeviceEvent.Connected -> wiredPorts.forEach { wire(event.serial, it) }
                            is DeviceEvent.Disconnected -> wiredPorts.forEach { unwire(event.serial, it) }
                        }
                    }
                    System.err.println("ADB device tracking ended; re-attaching in ${RECONNECT_DELAY_MS}ms")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    System.err.println("ADB device tracking failed; re-attaching in ${RECONNECT_DELAY_MS}ms: ${e.message}")
                }
                delay(RECONNECT_DELAY_MS.milliseconds)
            }
        }
    }

    private fun deviceEventFlow(): Flow<DeviceEvent> = callbackFlow {
        val deviceTrackingProcess = ProcessBuilder(adbPath, "track-devices")
            .redirectErrorStream(true)
            .start()

        launch {
            deviceTrackingProcess.inputStream
                .bufferedReader()
                .useLines { lines ->
                    lines.forEach { line ->
                        val serial = line.substringBefore("\t")
                            // Remove 4-digit hex prefix
                            .replaceFirst(Regex("^[0-9a-fA-F]{4}"), "")
                        val event = line.substringAfter("\t")

                        when (event) {
                            "device" -> trySend(DeviceEvent.Connected(serial))
                            "offline" -> trySend(DeviceEvent.Disconnected(serial))
                        }
                    }
                }
            // track-devices reached EOF (the process exited): complete the flow so startAutoWiring re-attaches.
            close()
        }

        awaitClose {
            deviceTrackingProcess.destroy()
        }
    }

    private fun wire(serial: String, port: Int) {
        println("Wiring ADB reverse for device $serial on port $port")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "tcp:$port", "tcp:$port")
        if (exitCode == 0) {
            wiredDevices.add(serial)
        } else {
            // e.g. "device offline" right after it turns ready, or an adb server version mismatch.
            // Surface it instead of recording a false success.
            System.err.println("Failed to wire ADB reverse for device $serial on port $port (exit=$exitCode): $output")
        }
    }

    private fun unwire(serial: String, port: Int) {
        println("Unwiring ADB reverse for device $serial on port $port")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        if (exitCode != 0) {
            System.err.println("Failed to unwire ADB reverse for device $serial on port $port (exit=$exitCode): $output")
        }
        wiredDevices.remove(serial)
    }

    override fun stopAutoWiring(port: Int) {
        wiredPorts.remove(port)
        wiredDevices.forEach { serial ->
            runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        }
        // Keep tracking as long as any port is still wired (e.g. only wss was turned off).
        if (wiredPorts.isEmpty()) {
            wiringJob?.cancel()
            wiringJob = null
            wiredDevices.clear()
        }
    }

    /** Runs an adb command to completion and returns its exit code together with its merged output. */
    private fun runAdb(vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(adbPath, *args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        return exitCode to output
    }
}

private sealed interface DeviceEvent {
    data class Connected(val serial: String) : DeviceEvent
    data class Disconnected(val serial: String) : DeviceEvent
}
