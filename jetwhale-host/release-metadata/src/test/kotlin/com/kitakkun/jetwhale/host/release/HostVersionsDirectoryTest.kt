package com.kitakkun.jetwhale.host.release

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class HostVersionsDirectoryTest {
    private val root: Path = Files.createTempDirectory("host-versions").resolve("host")
    private val hostVersionsDirectory = HostVersionsDirectory(root)

    @Test
    fun `lists the version directories newest first, and nothing else`() {
        listOf("1.0.0-alpha9", "1.0.0-alpha10", "1.0.0", "staging", "1.0.0-SNAPSHOT", "notes").forEach {
            Files.createDirectories(root.resolve(it))
        }
        Files.writeString(root.resolve("1.0.1"), "a file, not a version directory")

        assertEquals(listOf("1.0.0", "1.0.0-alpha10", "1.0.0-alpha9"), hostVersionsDirectory.installedVersions().map { it.version.name })
    }

    @Test
    fun `has no versions before the first download`() {
        assertEquals(emptyList(), hostVersionsDirectory.installedVersions())
        assertNull(hostVersionsDirectory.installedVersion("1.0.0-alpha15"))
    }

    @Test
    fun `names a version's files after the release`() {
        Files.createDirectories(root.resolve("1.0.0-alpha15"))
        val installedVersion = checkNotNull(hostVersionsDirectory.installedVersion("1.0.0-alpha15"))

        assertEquals(root.resolve("1.0.0-alpha15/release.json"), installedVersion.metadataFile)
        assertEquals(root.resolve("1.0.0-alpha15/release.json.sig"), installedVersion.signatureFile)
        assertEquals(root.resolve("1.0.0-alpha15/jetwhale-host-1.0.0-alpha15-linux-x64.jar"), installedVersion.jarFile("linux-x64"))
    }

    @Test
    fun `reads back the launcher state it wrote, and leaves no temporary file`() {
        val state = LauncherState(
            completedStartVersions = setOf(checkNotNull(HostVersion.parse("1.0.0-alpha14"))),
            setAsideVersions = setOf(checkNotNull(HostVersion.parse("1.0.0-alpha15"))),
        )

        hostVersionsDirectory.writeLauncherState(state)

        assertEquals(state, hostVersionsDirectory.readLauncherState())
        assertEquals(listOf("launcher-state.json"), root.listDirectoryEntries().map(Path::name))
    }

    @Test
    fun `reads a missing or unreadable launcher state as empty`() {
        assertEquals(LauncherState.EMPTY, hostVersionsDirectory.readLauncherState())

        Files.createDirectories(root)
        Files.writeString(root.resolve("launcher-state.json"), "{ broken")

        assertEquals(LauncherState.EMPTY, hostVersionsDirectory.readLauncherState())
    }

    @Test
    fun `deletes a version directory with its files`() {
        Files.createDirectories(root.resolve("1.0.0-alpha15"))
        Files.writeString(root.resolve("1.0.0-alpha15/release.json"), "{}")

        hostVersionsDirectory.delete(checkNotNull(hostVersionsDirectory.installedVersion("1.0.0-alpha15")))

        assertFalse(root.resolve("1.0.0-alpha15").exists())
    }
}
