package com.kitakkun.jetwhale.plugins.mainthread.agent

import kotlin.concurrent.AtomicInt

// Kotlin/Native has no API for reading another thread's stack, so the main thread's work could be
// timed here but not named.
internal actual fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?, hostConnected: () -> Boolean): MainThreadProbe = UnsupportedMainThreadProbe(
    platform = "Apple",
    reason = "Main-thread monitoring is not available on iOS or macOS yet.",
)

internal actual fun monitorLock(): MonitorLock = object : MonitorLock {
    private val held = AtomicInt(0)

    override fun lock() {
        @Suppress("ControlFlowWithEmptyBody")
        while (!held.compareAndSet(0, 1)) {
        }
    }

    override fun unlock() {
        held.value = 0
    }
}
