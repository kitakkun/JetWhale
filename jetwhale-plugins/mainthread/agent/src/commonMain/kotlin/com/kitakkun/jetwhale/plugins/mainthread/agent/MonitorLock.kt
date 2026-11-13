package com.kitakkun.jetwhale.plugins.mainthread.agent

/** A mutual-exclusion lock; a no-op where the platform runs everything on one thread. */
internal interface MonitorLock {
    fun lock()

    fun unlock()
}

internal expect fun monitorLock(): MonitorLock

internal inline fun <T> MonitorLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
