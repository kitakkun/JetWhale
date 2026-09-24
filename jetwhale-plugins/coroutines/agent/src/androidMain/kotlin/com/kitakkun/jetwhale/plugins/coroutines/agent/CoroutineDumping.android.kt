package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

// DebugProbes.install() fails on Android: ByteBuddy's self-attach reads the VM name through
// java.lang.management.ManagementFactory, which ART does not have.
internal actual fun dumpCoroutines(): CoroutineDump = CoroutineDump(
    text = "",
    unavailableReason = "DebugProbes cannot be installed on Android: they attach a JVM agent through ByteBuddy, which Android's runtime does not support",
)
