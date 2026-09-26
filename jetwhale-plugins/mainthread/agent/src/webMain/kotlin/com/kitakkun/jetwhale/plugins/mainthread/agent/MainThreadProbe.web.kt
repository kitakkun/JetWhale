package com.kitakkun.jetwhale.plugins.mainthread.agent

// The Long Tasks API reports how long the browser's main thread was busy but not with what, and the
// agent may run under Node, which has none. Until durations alone are worth a timeline, the web
// reports what it cannot do instead.
internal actual fun createMainThreadProbe(recorder: MainThreadRecorder, labels: () -> String?): MainThreadProbe = UnsupportedMainThreadProbe(
    platform = "Web",
    reason = "Main-thread monitoring is not available on the web yet.",
)

// JavaScript and Wasm run the agent on a single thread.
internal actual fun monitorLock(): MonitorLock = object : MonitorLock {
    override fun lock() = Unit

    override fun unlock() = Unit
}
