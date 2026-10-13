package com.kitakkun.jetwhale.host.release

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class HostJarCheckTest {
    private val directory: Path = Files.createTempDirectory("host-jar-check")
    private val content = "a jar".toByteArray()

    private val entry = HostPlatformRelease(
        url = "https://example.com/host.jar",
        size = content.size.toLong(),
        sha256 = "fc8bd814a3051a9717431182d5af2a2d654481aac82a028c05c73afa4ca38823",
        jvmArgs = emptyList(),
    )

    @Test
    fun `a jar with the entry's size and hash matches`() {
        val jar = directory.resolve("host.jar").apply { writeBytes(content) }

        assertEquals(HostJarCheck.Matches, entry.check(jar))
    }

    @Test
    fun `a jar that is not there is missing`() {
        assertEquals(HostJarCheck.Missing, entry.check(directory.resolve("absent.jar")))
    }

    @Test
    fun `a truncated jar fails on its size`() {
        val jar = directory.resolve("host.jar").apply { writeBytes(content.copyOf(content.size - 1)) }

        assertEquals(HostJarCheck.SizeMismatch(expected = content.size.toLong(), actual = content.size - 1L), entry.check(jar))
    }

    @Test
    fun `a jar of the same size with other bytes fails on its hash`() {
        val tampered = content.copyOf().also { it[0] = 'A'.code.toByte() }
        val jar = directory.resolve("host.jar").apply { writeBytes(tampered) }

        assertEquals(
            HostJarCheck.HashMismatch(expected = entry.sha256, actual = sha256Hex(jar)),
            entry.check(jar),
        )
    }

    @Test
    fun `hashes as sha256sum does`() {
        val jar = directory.resolve("abc.txt").apply { writeBytes("abc".toByteArray()) }

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(jar))
    }
}
