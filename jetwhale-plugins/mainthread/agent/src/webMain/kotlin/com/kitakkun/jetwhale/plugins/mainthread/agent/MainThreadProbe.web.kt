package com.kitakkun.jetwhale.plugins.mainthread.agent

// The browser's Long Tasks API reports how long the main thread was busy but not with what, and
// Node, where the agent may also run, has no such API.
internal actual fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?, hostConnected: () -> Boolean): MainThreadProbe = UnsupportedMainThreadProbe(
    platform = "Web",
    reason = "Main-thread monitoring is not available on the web yet.",
)

internal actual fun monitorLock(): MonitorLock = object : MonitorLock {
    override fun lock() = Unit

    override fun unlock() = Unit
}
