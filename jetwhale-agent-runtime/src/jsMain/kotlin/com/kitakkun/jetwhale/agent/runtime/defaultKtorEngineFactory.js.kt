package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.js.Js

internal actual fun defaultKtorEngineFactory(): io.ktor.client.engine.HttpClientEngineFactory<*> = Js
