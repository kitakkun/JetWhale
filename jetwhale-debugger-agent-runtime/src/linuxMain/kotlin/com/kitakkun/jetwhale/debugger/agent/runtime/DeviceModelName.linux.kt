package com.kitakkun.jetwhale.debugger.agent.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.gethostname

@OptIn(ExperimentalForeignApi::class)
internal actual fun getDeviceModelName(): String {
    val name = ByteArray(256)
    gethostname(name.refTo(0), name.size.convert())
    return name.toKString()
}
