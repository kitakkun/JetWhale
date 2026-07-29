package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent

/**
 * Runs a host command and returns the text it answered with.
 *
 * Host commands all answer with text, but they say so through a [com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult],
 * which is the shape every tool result shares. Tests assert on the text, so this unwraps it in one
 * place rather than at every call site.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal suspend fun HostMcpCommand.executeForText(arguments: JetWhaleMcpArguments): String = execute(arguments)
    .content
    .filterIsInstance<JetWhaleMcpContent.Text>()
    .joinToString(separator = "") { it.text }
