package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.curl.Curl

internal actual fun defaultKtorEngineFactory(): HttpClientEngineFactory<*> = Curl
