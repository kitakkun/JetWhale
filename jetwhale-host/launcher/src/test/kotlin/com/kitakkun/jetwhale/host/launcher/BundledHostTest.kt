package com.kitakkun.jetwhale.host.launcher

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledHostTest {
    private val directory = Files.createTempDirectory("bundled-host")

    @Test
    fun `reads the version and the jar the package carries`() {
        Files.writeString(directory.resolve("release.json"), hostMetadata("1.0.0-alpha13", "host".toByteArray()).encode())

        val chosenHostVersion = assertNotNull(BundledHost.read(directory)).chosenHostVersion

        assertEquals("1.0.0-alpha13", chosenHostVersion.version.name)
        assertEquals(directory.resolve("jetwhale-host.bundled"), chosenHostVersion.jar)
        assertTrue(chosenHostVersion.isBundled)
    }

    @Test
    fun `has no bundled version without readable metadata`() {
        assertNull(BundledHost.read(directory))

        Files.writeString(directory.resolve("release.json"), "{}")

        assertNull(BundledHost.read(directory))
    }
}
