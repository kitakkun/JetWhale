package com.kitakkun.jetwhale.plugins.mainthread.agent

import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind

/** The platform side: hooks the main thread while started, and unhooks everything when stopped. */
internal interface MainThreadProbe {
    val capabilities: MonitorCapabilities

    fun start()

    fun stop()
}

internal expect fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?): MainThreadProbe

/** Maps a StrictMode violation's class name (`DiskReadViolation`, …) to what the host shows. */
internal fun violationKindOf(className: String): ViolationKind = when (className.substringAfterLast('.')) {
    "DiskReadViolation" -> ViolationKind.DiskRead
    "DiskWriteViolation" -> ViolationKind.DiskWrite
    "NetworkViolation" -> ViolationKind.Network
    "CustomViolation" -> ViolationKind.CustomSlowCall
    "ResourceMismatchViolation" -> ViolationKind.ResourceMismatch
    "UnbufferedIoViolation" -> ViolationKind.UnbufferedIo
    else -> ViolationKind.Other
}

/** A probe for a platform whose main thread this agent cannot watch; it records nothing. */
internal class UnsupportedMainThreadProbe(platform: String, reason: String) : MainThreadProbe {
    override val capabilities = MonitorCapabilities(
        platform = platform,
        taskTiming = false,
        stackSampling = false,
        strictMode = false,
        frameTiming = false,
        note = reason,
    )

    override fun start() = Unit

    override fun stop() = Unit
}
