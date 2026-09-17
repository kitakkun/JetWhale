package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlin.time.Duration.Companion.seconds

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ExampleHostScopedPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin = ExampleHostScopedPlugin(context.adb)
}

/**
 * A **host-scoped** example plugin: its manifest entry declares `"scope": "host"`, so the host holds
 * a single instance of it that is created as soon as the plugin is enabled — with no app connected
 * and no session in existence. Its MCP tool therefore takes no `sessionId`.
 *
 * It is headless (no [com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi]) and does its work
 * through the host services it is handed at creation, here the shared adb runner.
 */
@OptIn(ExperimentalJetWhaleApi::class)
private class ExampleHostScopedPlugin(adb: JetWhaleAdb) :
    JetWhaleHostPlugin(),
    JetWhaleMcpCapablePlugin {

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(AdbVersionCommand(adb))
}

/** How long to let `adb version` run; it either answers at once or adb is not working. */
private val ADB_VERSION_TIMEOUT = 10.seconds

@OptIn(ExperimentalJetWhaleApi::class)
private class AdbVersionCommand(private val adb: JetWhaleAdb) : JetWhaleMcpCommand() {
    override val name = "com.kitakkun.jetwhale.example.hostscoped.adbVersion"
    override val description = "Reports the version of the adb the debug tool resolved, to show a host-scoped plugin working with no app connected."

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val result = try {
            adb.run("version", timeout = ADB_VERSION_TIMEOUT)
        } catch (e: JetWhaleAdbUnavailableException) {
            return JetWhaleMcpResult.text("adb is not available on this machine: ${e.message}")
        }
        return JetWhaleMcpResult.text("adb executable: ${adb.executable}\nexit code: ${result.exitCode}\n${result.output}")
    }
}
