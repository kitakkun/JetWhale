package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.winhttp.WinHttp

internal actual fun defaultKtorEngineFactory(): HttpClientEngineFactory<*> = WinHttp
