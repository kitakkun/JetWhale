package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.GetComputerNameW
import platform.windows.UINTVar
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun getDeviceModelName(): String {
    memScoped {
        val buffer = allocArray<WCHARVar>(256)
        val size = alloc<UINTVar>()
        size.value = 256u
        if (GetComputerNameW(buffer, size.ptr) != 0) {
            return buffer.toKString()
        } else {
            return "Unknown Windows Device"
        }
    }
}
