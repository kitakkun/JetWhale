package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HostReleaseMetadataTest {
    private val reader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases)

    @Test
    fun `reads back what it encodes`() {
        val metadata = sampleMetadata()

        assertEquals(HostReleaseMetadataResult.Read(metadata), reader.read(metadata.encode().toByteArray(), null))
    }

    @Test
    fun `ignores fields it does not know`() {
        val text = """
            {
              "format": 1,
              "version": "1.0.0-alpha14",
              "mainClass": "com.kitakkun.jetwhale.host.MainKt",
              "launcherContract": 1,
              "channel": "nightly",
              "runtime": { "javaFeatureVersion": 21, "modules": ["java.base"], "vendor": "corretto" },
              "jvmArgs": [],
              "platforms": {
                "linux-x64": { "url": "https://example.com/host.jar", "size": 3, "sha256": "${"a".repeat(64)}", "jvmArgs": [], "mirror": "x" }
              }
            }
        """.trimIndent()

        val read = assertIs<HostReleaseMetadataResult.Read>(reader.read(text.toByteArray(), null))
        assertEquals("1.0.0-alpha14", read.metadata.version)
    }

    @Test
    fun `refuses a format higher than it knows`() {
        val text = """{ "format": 2, "somethingNew": true }"""

        assertEquals(HostReleaseMetadataResult.NewerFormat(2), reader.read(text.toByteArray(), null))
    }

    @Test
    fun `refuses a file that is not complete metadata`() {
        listOf(
            "not json",
            "[]",
            """{ "version": "1.0.0" }""",
            """{ "format": 1, "version": "1.0.0" }""",
            sampleMetadata().copy(version = "1.0.0-SNAPSHOT").encode(),
            sampleMetadata().withMacJar { copy(sha256 = "A".repeat(64)) }.encode(),
            sampleMetadata().withMacJar { copy(sha256 = "abc") }.encode(),
            sampleMetadata().withMacJar { copy(size = -1) }.encode(),
        ).forEach { text ->
            assertIs<HostReleaseMetadataResult.Malformed>(reader.read(text.toByteArray(), null), text)
        }
    }

    @Test
    fun `reads nothing that the signature verifier does not trust`() {
        val metadata = sampleMetadata().encode().toByteArray()
        val signature = byteArrayOf(1, 2, 3)
        var checked: Pair<ByteArray, ByteArray?>? = null
        val rejecting = HostReleaseMetadataReader { bytes, sig ->
            checked = bytes to sig
            false
        }

        assertEquals(HostReleaseMetadataResult.Untrusted, rejecting.read(metadata, signature))
        val (checkedMetadata, checkedSignature) = assertNotNull(checked)
        assertEquals(metadata.toList(), checkedMetadata.toList())
        assertEquals(signature.toList(), checkedSignature?.toList())
    }

    @Test
    fun `puts the common JVM arguments before the platform's own`() {
        val metadata = sampleMetadata()

        assertEquals(
            listOf("-Dcompose.application.configure.swing.globals=true", "-Xdock:name=JetWhale Debugger"),
            metadata.jvmArgsFor("macos-arm64"),
        )
        assertEquals(listOf("-Dcompose.application.configure.swing.globals=true"), metadata.jvmArgsFor("linux-x64"))
    }

    @Test
    fun `a launcher that has everything the release needs runs it`() {
        assertNull(sampleMetadata().refusalOn(capableLauncher))
    }

    @Test
    fun `a launcher refuses a release that needs more than it has`() {
        val metadata = sampleMetadata()

        assertEquals(
            HostReleaseRefusal.NeedsNewerLauncher(2),
            metadata.copy(launcherContract = 2).refusalOn(capableLauncher),
        )
        assertEquals(
            HostReleaseRefusal.NeedsNewerJava(25),
            metadata.copy(runtime = metadata.runtime.copy(javaFeatureVersion = 25)).refusalOn(capableLauncher),
        )
        assertEquals(
            HostReleaseRefusal.MissingModules(listOf("java.net.http")),
            metadata.copy(runtime = metadata.runtime.copy(modules = metadata.runtime.modules + "java.net.http"))
                .refusalOn(capableLauncher),
        )
        assertEquals(
            HostReleaseRefusal.NoBuildForPlatform("windows-x64"),
            metadata.refusalOn(capableLauncher.copy(platformKey = "windows-x64")),
        )
        assertEquals(
            HostReleaseRefusal.DisallowedJvmArgument("-javaagent:evil.jar"),
            metadata.withMacJar { copy(jvmArgs = jvmArgs + "-javaagent:evil.jar") }.refusalOn(capableLauncher),
        )
    }

    private val capableLauncher = LauncherCapabilities(
        contract = 1,
        javaFeatureVersion = 21,
        modules = setOf("java.base", "java.desktop", "java.logging"),
        platformKey = "macos-arm64",
    )

    private fun sampleMetadata() = HostReleaseMetadata(
        format = 1,
        version = "1.0.0-alpha14",
        mainClass = "com.kitakkun.jetwhale.host.MainKt",
        launcherContract = 1,
        runtime = HostRuntimeRequirements(javaFeatureVersion = 21, modules = listOf("java.base", "java.desktop")),
        jvmArgs = listOf("-Dcompose.application.configure.swing.globals=true"),
        platforms = mapOf(
            "macos-arm64" to HostPlatformRelease(
                url = "https://example.com/host-macos-arm64.jar",
                size = 125156159,
                sha256 = "8b2863effc431681ccf2686981887a2b01180001fc9b01f66a975d63ee3947de",
                jvmArgs = listOf("-Xdock:name=JetWhale Debugger"),
            ),
            "linux-x64" to HostPlatformRelease(
                url = "https://example.com/host-linux-x64.jar",
                size = 119133124,
                sha256 = "a62592231a1d2980342640c2533f60bb6d89917816d7fa2f91373097989fbf16",
                jvmArgs = emptyList(),
            ),
        ),
    )

    private fun HostReleaseMetadata.withMacJar(change: HostPlatformRelease.() -> HostPlatformRelease) = copy(platforms = platforms + ("macos-arm64" to platforms.getValue("macos-arm64").change()))
}
