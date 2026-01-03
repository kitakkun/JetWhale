package com.kitakkun.jetwhale.host.data.server

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultADBAutoWiringService : ADBAutoWiringService {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val wiredDevices = ConcurrentSet<String>()
    private var wiringJob: Job? = null

    override fun startAutoWiring(port: Int) {
        if (wiringJob != null) return

        wiringJob = coroutineScope.launch {
            deviceEventFlow().collect { event ->
                when (event) {
                    is DeviceEvent.Connected -> {
                        wire(event.serial, port)
                    }

                    is DeviceEvent.Disconnected -> {
                        unwire(event.serial, port)
                    }
                }
            }
        }
    }

    private fun deviceEventFlow(): Flow<DeviceEvent> = callbackFlow {
        val deviceTrackingProcess = ProcessBuilder("adb", "track-devices")
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
                            "device" -> {
                                trySend(DeviceEvent.Connected(serial))
                            }

                            "offline" -> {
                                trySend(DeviceEvent.Disconnected(serial))
                            }
                        }
                    }
                }
        }

        awaitClose {
            deviceTrackingProcess.destroy()
        }
    }

    private fun wire(serial: String, port: Int) {
        println("Wiring ADB reverse for device $serial on port $port")
        ProcessBuilder("adb", "-s", serial, "reverse", "tcp:$port", "tcp:$port")
            .start()
            .waitFor()
        wiredDevices.add(serial)
    }

    private fun unwire(serial: String, port: Int) {
        println("Unwiring ADB reverse for device $serial on port $port")
        ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:$port")
            .start()
            .waitFor()
        wiredDevices.remove(serial)
    }

    override fun stopAutoWiring(port: Int) {
        wiringJob?.cancel()
        wiringJob = null
        wiredDevices.forEach { serial ->
            ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:$port")
                .start()
                .waitFor()
        }
        wiredDevices.clear()
    }
}

private sealed interface DeviceEvent {
    data class Connected(val serial: String) : DeviceEvent
    data class Disconnected(val serial: String) : DeviceEvent
}
