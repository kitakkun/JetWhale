package com.kitakkun.jetwhale.agent.compiler

import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Names shared by the registrar, the CLI processor and the Gradle plugin.
 *
 * The Gradle plugin repeats [PLUGIN_ID] and [ADDRESS_OPTION] in its own source — it must not depend
 * on the compiler plugin, whose classpath is `kotlin-compiler-embeddable`. Keeping them here as the
 * origin at least gives the duplication one obvious side to fix first.
 */
internal object JetWhaleAgentPluginNames {
    const val PLUGIN_ID: String = "com.kitakkun.jetwhale.agent"
    const val ADDRESS_OPTION: String = "buildMachineAddress"
}

/** The address `buildMachineWss` is rewritten to dial. Absent when the Gradle plugin found none. */
internal val BUILD_MACHINE_ADDRESS_KEY: CompilerConfigurationKey<String> =
    CompilerConfigurationKey.create("JetWhale build machine address")
