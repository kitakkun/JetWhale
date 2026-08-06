@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.kitakkun.jetwhale.agent.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

class JetWhaleAgentCommandLineProcessor : CommandLineProcessor {
    private val buildMachineAddress = CliOption(
        optionName = JetWhaleAgentPluginNames.ADDRESS_OPTION,
        valueDescription = "<ip-or-hostname>",
        description = "Address that buildMachineWss(port) is rewritten to dial.",
        // Optional: with no address the plugin leaves every call alone, and the runtime says why.
        // That is a better failure than refusing to compile a machine that has no LAN address.
        required = false,
        allowMultipleOccurrences = false,
    )

    override val pluginId: String = JetWhaleAgentPluginNames.PLUGIN_ID
    override val pluginOptions: Collection<CliOption> = listOf(buildMachineAddress)

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            buildMachineAddress.optionName -> configuration.put(BUILD_MACHINE_ADDRESS_KEY, value)
            else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
        }
    }
}
