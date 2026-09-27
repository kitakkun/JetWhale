package com.kitakkun.jetwhale.plugins.mainthread.agent

import kotlin.concurrent.AtomicInt

// A main run-loop observer could time long iterations here, but not name what ran: reading the main
// thread's stack from another thread has no Kotlin/Native API. Until that is worth building, Apple
// platforms report what they cannot do instead of an empty timeline.
internal actual fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?): MainThreadProbe = UnsupportedMainThreadProbe(
    platform = "Apple",
    reason = "Main-thread monitoring is not available on iOS or macOS yet.",
)

// Only the host's requests contend here on Apple platforms, and each holds the lock briefly.
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
