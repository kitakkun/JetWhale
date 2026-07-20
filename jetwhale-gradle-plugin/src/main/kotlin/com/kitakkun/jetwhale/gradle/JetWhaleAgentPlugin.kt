package com.kitakkun.jetwhale.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Configuration for the `com.kitakkun.jetwhale.agent` plugin.
 */
interface JetWhaleAgentExtension {
    /**
     * Whether to inject the build machine's host candidates (non-loopback IPv4 addresses + hostname)
     * into the app. When true (the default), the plugin generates a source file the app can apply so
     * `startJetWhale {}` reaches the build machine over the LAN with no connection block.
     *
     * Turn this off for CI builds, where the build machine's addresses are meaningless to the shipped
     * app: `jetwhale { injectBuildHostCandidates = false }`.
     */
    val injectBuildHostCandidates: Property<Boolean>
}

/**
 * Injects the build machine's host addresses into an app being debugged.
 *
 * At build time the plugin collects the machine's non-loopback IPv4 addresses and hostname and
 * generates a source file into the module's `commonMain` (or `main`) Kotlin source set exposing:
 *
 * ```kotlin
 * public fun applyJetWhaleBuildEnvironment()
 * ```
 *
 * Call it once before `startJetWhale {}` (e.g. at the top of your `initializeJetWhale()`); it
 * registers the captured addresses with the agent runtime so a physical device reaching the build
 * machine over the LAN needs no explicit host. The addresses are captured at build time, so they are
 * only correct while the build machine keeps the same addresses — the norm when the same machine
 * builds and debugs. Explicit `connection { host = ... }` config always takes precedence.
 */
class JetWhaleAgentPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("jetwhale", JetWhaleAgentExtension::class.java)
        extension.injectBuildHostCandidates.convention(true)

        val outputDir = project.layout.buildDirectory.dir("generated/jetwhale/kotlin")

        val generateTask = project.tasks.register<GenerateBuildEnvironmentTask>(
            "generateJetWhaleBuildEnvironment",
        ) {
            injectEnabled.set(extension.injectBuildHostCandidates)
            // Captured at configuration time on the build machine; staleness is acceptable (see class
            // KDoc) and the values are task inputs so a machine/address change re-runs generation.
            hostName.set(resolveHostName())
            addresses.set(resolveNonLoopbackIpv4Addresses())
            this.outputDir.set(outputDir)
        }

        // Wire the generated directory into the appropriate Kotlin source set and make compilation
        // depend on generation.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
            kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateTask)
        }
        val singleTargetIds = listOf(
            "org.jetbrains.kotlin.jvm",
            "org.jetbrains.kotlin.android",
        )
        singleTargetIds.forEach { pluginId ->
            project.plugins.withId(pluginId) {
                val kotlin = project.extensions.getByType<KotlinSingleTargetExtension<*>>()
                kotlin.sourceSets.getByName("main").kotlin.srcDir(generateTask)
            }
        }
    }

    private fun resolveHostName(): String? = try {
        InetAddress.getLocalHost().hostName
    } catch (e: Exception) {
        null
    }

    /** Enumerates the build machine's up, non-loopback IPv4 addresses. */
    private fun resolveNonLoopbackIpv4Addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .distinct()
            .toList()
    } catch (e: Exception) {
        emptyList()
    }
}
