package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ExampleHeadlessPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = ExampleHeadlessPlugin()
}

/**
 * A **headless** example plugin: it does not implement
 * [com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi], so the host renders no scene for it and
 * marks it as having no screen. Its work is done entirely through the MCP tool it publishes, which
 * is the usual reason to write a plugin with no UI at all.
 */
@OptIn(ExperimentalJetWhaleApi::class)
private class ExampleHeadlessPlugin :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(EchoCommand())
}

@OptIn(ExperimentalJetWhaleApi::class)
private class EchoCommand : JetWhaleMcpCommand() {
    override val name = "com.kitakkun.jetwhale.example.headless.echo"
    override val description = "Echoes the given text back, to show a headless plugin doing its work over MCP."

    private val text by string("Text to echo back.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = arguments[text]
}
