package com.kitakkun.jetwhale.host.release.tool

import com.kitakkun.jetwhale.host.release.HostJarCheck
import com.kitakkun.jetwhale.host.release.HostPlatformRelease
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostReleaseMetadataResult
import com.kitakkun.jetwhale.host.release.HostRuntimeRequirements
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.check
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WriteHostReleaseMetadataTest {
    private val directory: Path = Files.createTempDirectory("host-release-metadata")
    private val output: Path = directory.resolve("jetwhale-host-1.0.0-alpha14.json")
    private val macJar = directory.resolve("jetwhale-host-1.0.0-alpha14-macos-arm64.jar").apply { writeBytes("mac".toByteArray()) }
    private val linuxJar = directory.resolve("jetwhale-host-1.0.0-alpha14-linux-x64.jar").apply { writeBytes("linux!".toByteArray()) }

    @Test
    fun `writes an entry per jar that the launcher's reader verifies`() {
        val problems = writeHostReleaseMetadata(
            baseArguments(version = "1.0.0-alpha14") + listOf(
                "--jvm-arg", "-Dcompose.application.configure.swing.globals=true",
                "--platform-jvm-arg", "macos-arm64=-Dapple.awt.application.appearance=system",
                "--platform-jvm-arg", "macos-arm64=--enable-native-access=ALL-UNNAMED",
                "--jar", "macos-arm64=$macJar",
                "--jar", "linux-x64=$linuxJar",
            ),
        )

        assertEquals(emptyList(), problems)
        val metadata = assertIs<HostReleaseMetadataResult.Read>(readOutput()).metadata
        assertEquals("1.0.0-alpha14", metadata.version.name)
        assertEquals("com.kitakkun.jetwhale.host.MainKt", metadata.mainClass)
        assertEquals(1, metadata.launcherContract)
        assertEquals(HostRuntimeRequirements(javaFeatureVersion = 21, modules = listOf("java.base", "java.desktop")), metadata.runtime)
        assertEquals(listOf("-Dcompose.application.configure.swing.globals=true"), metadata.jvmArgs)
        assertEquals(
            HostPlatformRelease(
                url = "https://github.com/kitakkun/JetWhale/releases/download/1.0.0-alpha14/jetwhale-host-1.0.0-alpha14-macos-arm64.jar",
                size = 3,
                sha256 = "348a629f5ceed032c3e8706ec47d9bfafb00fb4250b018dd965435ca50cb836e",
                jvmArgs = listOf("-Dapple.awt.application.appearance=system", "--enable-native-access=ALL-UNNAMED"),
            ),
            metadata.platforms.getValue("macos-arm64"),
        )
        assertEquals(emptyList(), metadata.platforms.getValue("linux-x64").jvmArgs)
        assertEquals(HostJarCheck.Matches, metadata.platforms.getValue("macos-arm64").check(macJar))
        assertEquals(HostJarCheck.Matches, metadata.platforms.getValue("linux-x64").check(linuxJar))
    }

    @Test
    fun `writes nothing when a jar is missing`() {
        val problems = writeHostReleaseMetadata(
            baseArguments(version = "1.0.0-alpha14") + listOf("--jar", "windows-x64=${directory.resolve("absent.jar")}"),
        )

        assertTrue(problems.single().startsWith("No windows-x64 jar"), problems.toString())
        assertFalse(output.exists())
    }

    @Test
    fun `writes nothing for a JVM argument the launcher would refuse`() {
        val problems = writeHostReleaseMetadata(
            baseArguments(version = "1.0.0-alpha14") + listOf(
                "--platform-jvm-arg",
                "macos-arm64=-javaagent:agent.jar",
                "--jar",
                "macos-arm64=$macJar",
            ),
        )

        assertEquals(listOf("-javaagent:agent.jar is not a JVM argument the launcher contract allows"), problems)
        assertFalse(output.exists())
    }

    @Test
    fun `writes nothing for a version that is not a release`() {
        val problems = writeHostReleaseMetadata(
            baseArguments(version = "1.0.0-alpha14-SNAPSHOT") + listOf("--jar", "macos-arm64=$macJar"),
        )

        assertEquals(listOf("1.0.0-alpha14-SNAPSHOT is not a release version"), problems)
        assertFalse(output.exists())
    }

    @Test
    fun `writes nothing for platform arguments without a jar`() {
        val problems = writeHostReleaseMetadata(
            baseArguments(version = "1.0.0-alpha14") + listOf(
                "--platform-jvm-arg",
                "windows-x64=-Xmx4g",
                "--jar",
                "macos-arm64=$macJar",
            ),
        )

        assertEquals(listOf("windows-x64 has JVM arguments but no --jar"), problems)
        assertFalse(output.exists())
    }

    @Test
    fun `explains arguments it cannot use`() {
        assertEquals(listOf("Unknown option --sign"), writeHostReleaseMetadata(listOf("--sign", "key")))
        assertEquals(listOf("Expected a value after --output"), writeHostReleaseMetadata(listOf("--output")))
        assertEquals(listOf("Expected at least one --jar"), writeHostReleaseMetadata(baseArguments(version = "1.0.0-alpha14")))
        assertEquals(
            listOf("Expected one --jar per platform"),
            writeHostReleaseMetadata(baseArguments(version = "1.0.0-alpha14") + listOf("--jar", "macos-arm64=$macJar", "--jar", "macos-arm64=$linuxJar")),
        )
        assertEquals(
            listOf("Expected <os-arch>=<value> after --jar, but was $macJar"),
            writeHostReleaseMetadata(baseArguments(version = "1.0.0-alpha14") + listOf("--jar", "$macJar")),
        )
        assertEquals(
            listOf("Expected --version exactly once"),
            writeHostReleaseMetadata(baseArguments(version = "1.0.0-alpha14") + listOf("--version", "1.0.0", "--jar", "macos-arm64=$macJar")),
        )
    }

    private fun baseArguments(version: String) = listOf(
        "--output", "$output",
        "--version", version,
        "--main-class", "com.kitakkun.jetwhale.host.MainKt",
        "--java-feature-version", "21",
        "--module", "java.base",
        "--module", "java.desktop",
    )

    private fun readOutput() = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases)
        .read(Files.readAllBytes(output), null)
}
