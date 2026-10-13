package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostReleaseMetadataTest {
    private val reader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases)

    @Test
    fun `reads back what it encodes`() {
        val metadata = sampleHostReleaseMetadata()

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
        assertEquals("1.0.0-alpha14", read.metadata.version.name)
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
            sampleHostReleaseMetadata().copy(format = 0).encode(),
            sampleHostReleaseMetadata().copy(format = -1).encode(),
            sampleHostReleaseMetadata().encode().replace("\"1.0.0-alpha14\"", "\"1.0.0-SNAPSHOT\""),
            sampleHostReleaseMetadata().withMacJar { copy(sha256 = "A".repeat(64)) }.encode(),
            sampleHostReleaseMetadata().withMacJar { copy(sha256 = "abc") }.encode(),
            sampleHostReleaseMetadata().withMacJar { copy(size = -1) }.encode(),
        ).forEach { text ->
            assertIs<HostReleaseMetadataResult.Malformed>(reader.read(text.toByteArray(), null), text)
        }
    }

    @Test
    fun `answers deeply nested JSON with a result instead of overflowing the stack`() {
        val nested = "[".repeat(10_000) + "]".repeat(10_000)
        val metadata = sampleHostReleaseMetadata()

        assertEquals(
            HostReleaseMetadataResult.Read(metadata),
            reader.read(metadata.encode().replaceFirst("{", """{ "x": $nested,""").toByteArray(), null),
        )
        assertIs<HostReleaseMetadataResult.Malformed>(reader.read("""{ "format": 1, "jvmArgs": $nested }""".toByteArray(), null))
    }

    @Test
    fun `reads nothing that the signature verifier does not trust`() {
        val metadata = sampleHostReleaseMetadata().encode().toByteArray()
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
        val metadata = sampleHostReleaseMetadata()

        assertEquals(
            listOf("-Dcompose.application.configure.swing.globals=true", "-Dapple.awt.application.appearance=system"),
            metadata.jvmArgsFor("macos-arm64"),
        )
        assertEquals(listOf("-Dcompose.application.configure.swing.globals=true"), metadata.jvmArgsFor("linux-x64"))
    }
}
