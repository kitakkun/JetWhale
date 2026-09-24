package com.kitakkun.jetwhale.plugins.mainthread.agent

import java.util.concurrent.locks.ReentrantLock

internal actual fun monitorLock(): MonitorLock = object : MonitorLock {
    private val lock = ReentrantLock()

    override fun lock() = lock.lock()

    override fun unlock() = lock.unlock()
}
