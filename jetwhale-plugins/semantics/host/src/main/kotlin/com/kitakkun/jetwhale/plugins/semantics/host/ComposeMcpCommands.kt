package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

// Shared by the Compose Semantics Inspector's MCP command classes (one class per file in this package).

/** Tool names are globally unique across plugins, so they carry the pluginId by convention. */
internal const val TOOL_PREFIX = "com.kitakkun.jetwhale.semantics"

/**
 * Every tool here reaches into the running app, so a disconnected or unresponsive agent is a normal
 * outcome rather than a bug: it is reported to the caller as a failed result instead of failing
 * the MCP server.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun appDidNotAnswerResult(failure: JetWhaleMessagingException): JetWhaleMcpResult = JetWhaleMcpResult.error("the app did not answer: ${failure.message}")
