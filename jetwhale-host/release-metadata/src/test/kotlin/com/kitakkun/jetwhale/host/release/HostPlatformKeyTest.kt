package com.kitakkun.jetwhale.host.release

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostPlatformKeyTest {
    @Test
    fun `names the machines releases are built for as the release assets do`() {
        assertEquals("macos-arm64", hostPlatformKey(osName = "Mac OS X", osArch = "aarch64"))
        assertEquals("linux-x64", hostPlatformKey(osName = "Linux", osArch = "amd64"))
        assertEquals("windows-x64", hostPlatformKey(osName = "Windows 11", osArch = "amd64"))
        assertEquals("macos-x64", hostPlatformKey(osName = "Mac OS X", osArch = "x86_64"))
    }

    @Test
    fun `has no key for an OS or architecture no release could be built for`() {
        assertNull(hostPlatformKey(osName = "SunOS", osArch = "amd64"))
        assertNull(hostPlatformKey(osName = "Linux", osArch = "riscv64"))
    }
}
