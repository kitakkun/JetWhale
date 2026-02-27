package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun messagingServiceCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.IO
