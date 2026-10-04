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
    private val versions = HostVersionsDirectory(root)

    @Test
    fun `lists the version directories newest first, and nothing else`() {
        listOf("1.0.0-alpha9", "1.0.0-alpha10", "1.0.0", "staging", "1.0.0-SNAPSHOT", "notes").forEach {
            Files.createDirectories(root.resolve(it))
        }
        Files.writeString(root.resolve("1.0.1"), "a file, not a version directory")

        assertEquals(listOf("1.0.0", "1.0.0-alpha10", "1.0.0-alpha9"), versions.installedVersions().map(InstalledHostVersion::name))
    }

    @Test
    fun `has no versions before the first download`() {
        assertEquals(emptyList(), versions.installedVersions())
        assertNull(versions.installedVersion("1.0.0-alpha15"))
    }

    @Test
    fun `names a version's files after the release`() {
        Files.createDirectories(root.resolve("1.0.0-alpha15"))
        val installed = checkNotNull(versions.installedVersion("1.0.0-alpha15"))

        assertEquals(root.resolve("1.0.0-alpha15/release.json"), installed.metadataFile)
        assertEquals(root.resolve("1.0.0-alpha15/release.json.sig"), installed.signatureFile)
        assertEquals(root.resolve("1.0.0-alpha15/jetwhale-host-1.0.0-alpha15-linux-x64.jar"), installed.jar("linux-x64"))
    }

    @Test
    fun `reads back the launcher state it wrote, and leaves no temporary file`() {
        val state = LauncherState(completedStarts = setOf("1.0.0-alpha14"), setAside = setOf("1.0.0-alpha15"))

        versions.writeLauncherState(state)

        assertEquals(state, versions.readLauncherState())
        assertEquals(listOf("launcher-state.json"), root.listDirectoryEntries().map(Path::name))
    }

    @Test
    fun `reads a missing or unreadable launcher state as empty`() {
        assertEquals(LauncherState.EMPTY, versions.readLauncherState())

        Files.createDirectories(root)
        Files.writeString(root.resolve("launcher-state.json"), "{ broken")

        assertEquals(LauncherState.EMPTY, versions.readLauncherState())
    }

    @Test
    fun `deletes a version directory with its files`() {
        Files.createDirectories(root.resolve("1.0.0-alpha15"))
        Files.writeString(root.resolve("1.0.0-alpha15/release.json"), "{}")

        versions.delete(checkNotNull(versions.installedVersion("1.0.0-alpha15")))

        assertFalse(root.resolve("1.0.0-alpha15").exists())
    }
}
