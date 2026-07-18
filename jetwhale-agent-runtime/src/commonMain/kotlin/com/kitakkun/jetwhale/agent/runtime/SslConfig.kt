package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.engine.HttpClientEngineConfig

/**
 * Configures SSL settings for the HTTP client engine.
 *
 * @param sslConfiguration The SSL configuration containing trusted certificates.
 */
internal expect fun HttpClientEngineConfig.configureSsl(sslConfiguration: JetWhaleSslConfiguration)
