package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

internal actual fun dumpCoroutines(): CoroutineDump = CoroutineDump(text = "", unavailableReason = "the coroutines library's DebugProbes rewrite bytecode through a JVM agent, which Android's runtime cannot load")
