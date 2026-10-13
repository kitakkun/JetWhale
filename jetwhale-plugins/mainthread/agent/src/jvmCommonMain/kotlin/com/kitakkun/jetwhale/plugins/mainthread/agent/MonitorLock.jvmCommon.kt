package com.kitakkun.jetwhale.plugins.mainthread.agent

import java.util.concurrent.locks.ReentrantLock

internal actual fun monitorLock(): MonitorLock = object : MonitorLock {
    private val reentrantLock = ReentrantLock()

    override fun lock() = reentrantLock.lock()

    override fun unlock() = reentrantLock.unlock()
}
