package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import platform.posix.RTLD_DEFAULT
import platform.posix.dlsym

@OptIn(ExperimentalForeignApi::class)
internal actual fun enableApplicationAccessibilityOnSimulator() {
    // libAccessibility has no public header; UIKit loads it into every app.
    val isEnabled = dlsym(RTLD_DEFAULT, "_AXSApplicationAccessibilityEnabled")?.reinterpret<CFunction<() -> UByte>>() ?: return
    val setEnabled = dlsym(RTLD_DEFAULT, "_AXSApplicationAccessibilitySetEnabled")?.reinterpret<CFunction<(UByte) -> Unit>>() ?: return
    if (isEnabled() == 0.toUByte()) setEnabled(1.toUByte())
}
