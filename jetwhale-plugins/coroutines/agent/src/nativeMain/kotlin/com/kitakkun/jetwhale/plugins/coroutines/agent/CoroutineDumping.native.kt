package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

internal actual fun dumpCoroutines(): CoroutineDump = CoroutineDump(text = "", unavailableReason = "Kotlin/Native has no DebugProbes")
