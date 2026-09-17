package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListNavKeyTypesCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listNavKeyTypes"
    override val description =
        "Lists the NavKey types this app can be navigated to, each with its fields and a ready-to-fill JSON template. Fill a template in and pass it to pushNavKey."

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = JetWhaleMcpResult.text(controller.keyTypes().toMcpJson().toString())
}
