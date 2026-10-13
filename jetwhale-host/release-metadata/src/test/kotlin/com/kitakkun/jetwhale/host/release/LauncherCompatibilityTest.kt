package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LauncherCompatibilityTest {
    private val capableLauncher = LauncherCapabilities(
        contract = 1,
        javaFeatureVersion = 21,
        modules = setOf("java.base", "java.desktop", "java.logging"),
        platformKey = "macos-arm64",
        jvmArguments = listOf("-Dcompose.application.configure.swing.globals=true", "--enable-native-access=ALL-UNNAMED"),
    )

    private val compatibility = LauncherCompatibility(capableLauncher)

    @Test
    fun `a launcher that has everything the release needs runs it`() {
        assertNull(compatibility.refusalOf(sampleHostReleaseMetadata()))
    }

    @Test
    fun `a launcher refuses a release that needs more than it has`() {
        val metadata = sampleHostReleaseMetadata()

        assertEquals(
            HostReleaseRefusal.NeedsNewerLauncher(2),
            compatibility.refusalOf(metadata.copy(launcherContract = 2)),
        )
        assertEquals(
            HostReleaseRefusal.NeedsNewerJava(25),
            compatibility.refusalOf(metadata.copy(runtime = metadata.runtime.copy(javaFeatureVersion = 25))),
        )
        assertEquals(
            HostReleaseRefusal.MissingModules(listOf("java.net.http")),
            compatibility.refusalOf(metadata.copy(runtime = metadata.runtime.copy(modules = metadata.runtime.modules + "java.net.http"))),
        )
        assertEquals(
            HostReleaseRefusal.NoBuildForPlatform("windows-x64"),
            LauncherCompatibility(capableLauncher.copy(platformKey = "windows-x64")).refusalOf(metadata),
        )
        assertEquals(
            HostReleaseRefusal.DisallowedJvmArgument("-javaagent:evil.jar"),
            compatibility.refusalOf(metadata.withMacJar { copy(jvmArgs = jvmArgs + "-javaagent:evil.jar") }),
        )
    }

    @Test
    fun `a launcher runs a release that asks for any system property, and for other JVM arguments its JVM started with`() {
        val metadata = sampleHostReleaseMetadata().withMacJar { copy(jvmArgs = jvmArgs + "--enable-native-access=ALL-UNNAMED" + "-Dnew.property=1") }

        assertNull(compatibility.refusalOf(metadata))
    }

    @Test
    fun `a launcher refuses a release that needs a module of its runtime that a host cannot see`() {
        val launcherCompatibility = LauncherCompatibility(capableLauncher.copy(modules = runtimeModulesVisibleToHost()))
        val metadata = sampleHostReleaseMetadata()
        assertTrue(ModuleLayer.boot().findModule("jdk.attach").isPresent, "jdk.attach is in this runtime")

        assertNull(launcherCompatibility.refusalOf(metadata))
        assertEquals(
            HostReleaseRefusal.MissingModules(listOf("jdk.attach")),
            launcherCompatibility.refusalOf(metadata.copy(runtime = metadata.runtime.copy(modules = metadata.runtime.modules + "java.sql" + "jdk.attach"))),
        )
    }

    @Test
    fun `a launcher refuses a release that needs a JVM argument its own JVM did not start with`() {
        val metadata = sampleHostReleaseMetadata().withMacJar { copy(jvmArgs = jvmArgs + "-Xmx8g") }

        assertEquals(HostReleaseRefusal.MissingJvmArgument("-Xmx8g"), compatibility.refusalOf(metadata))
        assertNull(LauncherCompatibility(capableLauncher.copy(jvmArguments = capableLauncher.jvmArguments + "-Xmx8g")).refusalOf(metadata))
    }
}
