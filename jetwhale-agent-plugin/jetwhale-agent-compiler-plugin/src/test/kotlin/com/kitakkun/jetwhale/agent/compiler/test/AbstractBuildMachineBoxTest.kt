package com.kitakkun.jetwhale.agent.compiler.test

import com.kitakkun.jetwhale.agent.compiler.BUILD_MACHINE_ADDRESS_KEY
import com.kitakkun.jetwhale.agent.compiler.JetWhaleAgentCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirBlackBoxCodegenTestBase
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

/** The address the fixtures assert on: RFC 5737 TEST-NET-2, documentation-only. */
const val TEST_BUILD_MACHINE_ADDRESS: String = "198.51.100.7"

/**
 * Runs `testData/box` through the real compiler with this plugin registered.
 *
 * The sample module already proves the shipped JAR transforms a consumer's code; this proves the
 * transform's *shape*. Fixtures carry `// DUMP_IR`, so the framework writes an `.ir.txt` golden file
 * next to each and fails on any later divergence — which catches structural drift (a moved argument
 * slot, a lost source offset, an IR node that stops validating) that a behavioural test can only
 * notice if it happens to change the program's output.
 */
abstract class AbstractBuildMachineBoxTest : AbstractFirBlackBoxCodegenTestBase(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider() = EnvironmentBasedStandardLibrariesPathProvider

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            defaultDirectives {
                +CodegenTestDirectives.DUMP_IR
                // The default box pipeline runs D8/R8, which is not on this classpath and has
                // nothing to say about a plugin that does not target Android specifically.
                +CodegenTestDirectives.IGNORE_DEXING
                +JvmEnvironmentConfigurationDirectives.FULL_JDK
            }
            useConfigurators(::BuildMachinePluginConfigurator)
        }
    }
}

/**
 * Registers the plugin inside the test compiler, with an address supplied.
 *
 * Supplying one is what makes the registrar install the IR extension at all — the no-address path
 * registers nothing, which the sample covers from the other side.
 */
internal class BuildMachinePluginConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    private val registrar = JetWhaleAgentCompilerPluginRegistrar()

    @OptIn(ExperimentalCompilerApi::class)
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        configuration.put(BUILD_MACHINE_ADDRESS_KEY, TEST_BUILD_MACHINE_ADDRESS)
        with(registrar) { registerExtensions(configuration) }
    }
}
