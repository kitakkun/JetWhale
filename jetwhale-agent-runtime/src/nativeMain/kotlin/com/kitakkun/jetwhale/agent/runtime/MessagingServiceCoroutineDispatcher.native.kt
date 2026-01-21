package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual fun messagingServiceCoroutineDispatcher(): CoroutineDispatcher {
    return Dispatchers.IO
}
