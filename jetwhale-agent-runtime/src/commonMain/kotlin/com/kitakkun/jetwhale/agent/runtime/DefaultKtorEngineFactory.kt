package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Provides the default Ktor HTTP client engine factory for the JetWhale agent runtime.
 *
 * @return An instance of [HttpClientEngineFactory] suitable for the current platform.
 */
internal expect fun defaultKtorEngineFactory(): HttpClientEngineFactory<*>
