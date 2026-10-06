package com.kitakkun.jetwhale.host.launcher

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledHostDirectoryTest {
    private val directory = Files.createTempDirectory("bundled-host")

    @Test
    fun `reads the version and the jar the package carries`() {
        Files.writeString(directory.resolve("release.json"), hostMetadata("1.0.0-alpha13", "host".toByteArray()).encode())

        val chosenHostJar = assertNotNull(BundledHostDirectory(directory).readHostJar())

        assertEquals("1.0.0-alpha13", chosenHostJar.version.name)
        assertEquals(directory.resolve("jetwhale-host.bundled"), chosenHostJar.path)
        assertTrue(chosenHostJar.isBundled)
    }

    @Test
    fun `has no bundled version without readable metadata`() {
        assertNull(BundledHostDirectory(directory).readHostJar())

        Files.writeString(directory.resolve("release.json"), "{}")

        assertNull(BundledHostDirectory(directory).readHostJar())
    }
}
