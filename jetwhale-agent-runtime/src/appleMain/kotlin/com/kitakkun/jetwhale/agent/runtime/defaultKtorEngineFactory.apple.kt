package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.darwin.Darwin

internal actual fun defaultKtorEngineFactory(): io.ktor.client.engine.HttpClientEngineFactory<*> = Darwin
