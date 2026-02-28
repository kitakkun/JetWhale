package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun defaultKtorEngineFactory(): HttpClientEngineFactory<*> = CIO
