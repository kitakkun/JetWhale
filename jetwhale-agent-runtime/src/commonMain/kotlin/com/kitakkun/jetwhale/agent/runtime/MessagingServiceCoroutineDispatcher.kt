package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.CoroutineDispatcher

expect fun messagingServiceCoroutineDispatcher(): CoroutineDispatcher
