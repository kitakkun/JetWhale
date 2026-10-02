package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent

/** Runs a host command and returns the single text block every host command answers with. */
internal suspend fun HostMcpCommand.executeForText(arguments: JetWhaleMcpArguments): String = (execute(arguments).content.single() as JetWhaleMcpContent.Text).text
