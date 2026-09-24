package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

/** Every coroutine with its suspension stack, where the platform and the app make that possible. */
internal expect fun dumpCoroutines(): CoroutineDump
