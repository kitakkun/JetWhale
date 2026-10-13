package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
