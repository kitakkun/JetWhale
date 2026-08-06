@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.kitakkun.jetwhale.agent.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

class JetWhaleAgentCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = JetWhaleAgentPluginNames.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // No address: every buildMachineWss call is left as written, and its own body explains the
        // silence at runtime. Registering nothing keeps this compilation identical to an unplugged
        // one rather than half-transformed.
        val address = configuration.get(BUILD_MACHINE_ADDRESS_KEY) ?: return
        val messageCollector = configuration.get(
            CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            MessageCollector.NONE,
        )
        IrGenerationExtension.registerExtension(
            BuildMachineIrGenerationExtension(address = address, messageCollector = messageCollector),
        )
    }
}
