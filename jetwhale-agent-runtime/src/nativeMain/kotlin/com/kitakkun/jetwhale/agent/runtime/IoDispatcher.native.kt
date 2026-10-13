package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
