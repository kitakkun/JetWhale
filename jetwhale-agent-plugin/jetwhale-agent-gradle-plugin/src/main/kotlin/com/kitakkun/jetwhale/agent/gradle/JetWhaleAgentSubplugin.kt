package com.kitakkun.jetwhale.agent.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Bakes the build machine's address into `buildMachineWss(port)` calls in this module.
 *
 * Applied per-module rather than to a whole project on purpose. The address is a compile task input,
 * so it invalidates every compilation it reaches; keeping it on the one module that declares the
 * endpoint means changing networks recompiles that module rather than the build.
 */
abstract class JetWhaleAgentSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("jetwhaleAgent", JetWhaleAgentExtension::class.java)
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.kitakkun.jetwhale",
        artifactId = "jetwhale-agent-compiler-plugin",
        version = VERSION,
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(JetWhaleAgentExtension::class.java)
        val detected = project.providers.of(BuildMachineAddressSource::class.java) { }
        return extension.address.orElse(detected).map { address ->
            // A plain SubpluginOption, never InternalSubpluginOption: only the former is carried into
            // the compile task's inputs, and an address outside the inputs would let a stale one
            // survive in an "up to date" build.
            listOf(SubpluginOption(key = BUILD_MACHINE_ADDRESS_OPTION, value = address))
        }.orElse(emptyList())
    }
}

/** Configuration for the `com.kitakkun.jetwhale.agent` plugin. */
abstract class JetWhaleAgentExtension {
    /**
     * The address to bake in, overriding detection.
     *
     * Detection asks the routing table which source address would reach the wider network, which is
     * the right answer on an ordinary machine. Set this where it is not: several interfaces where the
     * device is on the other one, a VPN capturing the default route, a host reached through a
     * forwarded port.
     *
     * When neither this nor detection yields an address — an offline machine, say — no address is
     * passed, calls are left as written, and the agent explains at runtime rather than the build
     * failing.
     */
    abstract val address: Property<String>
}

private const val COMPILER_PLUGIN_ID = "com.kitakkun.jetwhale.agent"
private const val BUILD_MACHINE_ADDRESS_OPTION = "buildMachineAddress"
