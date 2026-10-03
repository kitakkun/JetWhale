package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbException
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpTextCommand
import kotlin.time.Duration.Companion.seconds

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
@OptIn(ExperimentalJetWhaleApi::class)
class ExampleHeadlessPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin = ExampleHeadlessPlugin(context.adb)
}

/**
 * A **headless** example plugin: it does not implement
 * [com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi], so the host renders no scene for it and
 * marks it as having no screen. Its work is done entirely through the MCP tools it publishes, which
 * is the usual reason to write a plugin with no UI at all. One of them uses the host's own adb,
 * handed to the factory in its [JetWhaleHostPluginContext].
 */
@OptIn(ExperimentalJetWhaleApi::class)
private class ExampleHeadlessPlugin(adb: JetWhaleAdb) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(EchoCommand(), AdbVersionCommand(adb))
}

@OptIn(ExperimentalJetWhaleApi::class)
private class EchoCommand : JetWhaleMcpTextCommand() {
    override val name = "com.kitakkun.jetwhale.example.headless.echo"
    override val description = "Echoes the given text back, to show a headless plugin doing its work over MCP."

    private val text by string("Text to echo back.")

    override suspend fun executeText(arguments: JetWhaleMcpArguments): String = arguments[text]
}

@OptIn(ExperimentalJetWhaleApi::class)
private class AdbVersionCommand(private val adb: JetWhaleAdb) : JetWhaleMcpCommand() {
    override val name = "com.kitakkun.jetwhale.example.headless.adbVersion"
    override val description = "Reports the version of the adb the debug tool uses, to show a plugin running adb with no app connected."

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val result = try {
            adb.run("version", timeout = 10.seconds)
        } catch (e: JetWhaleAdbException) {
            return JetWhaleMcpResult.error("adb could not report its version: ${e.message}")
        }
        if (result.exitCode != 0) return JetWhaleMcpResult.error("adb version exited with code ${result.exitCode}: ${result.output}")
        return JetWhaleMcpResult.text(result.output)
    }
}
