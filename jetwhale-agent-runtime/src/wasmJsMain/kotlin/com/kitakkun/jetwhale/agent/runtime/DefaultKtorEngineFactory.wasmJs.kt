package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

internal actual fun defaultKtorEngineFactory(): HttpClientEngineFactory<*> = Js
