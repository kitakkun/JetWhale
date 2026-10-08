package com.kitakkun.jetwhale.plugins.xctestrunner

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunnerStateDirectoryTest {
    private val folder: File = Files.createTempDirectory("runner-state").toFile()
    private val stateDirectory = RunnerStateDirectory(File(folder, "runners"))
    private val runnerState = RunnerState(protocolVersion = 1, pid = 500, port = 20_000, token = "token-1", developmentTeam = null)

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun `a recorded runner reads back as written`() {
        stateDirectory.writeRunnerState("SIM-1", runnerState)

        assertEquals(runnerState, stateDirectory.readRunnerState("SIM-1"))
    }

    @Test
    fun `only the record's owner can read it, since it holds the token`() {
        assumeTrue("file permissions are POSIX ones only where runners exist: macOS", "posix" in FileSystems.getDefault().supportedFileAttributeViews())

        stateDirectory.writeRunnerState("SIM-1", runnerState)

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(File(folder, "runners/SIM-1.json").toPath())))
    }

    @Test
    fun `a record is deleted only by the runner it describes`() {
        stateDirectory.writeRunnerState("SIM-1", runnerState)

        stateDirectory.deleteRunnerState("SIM-1", pid = 501)
        assertEquals(runnerState, stateDirectory.readRunnerState("SIM-1"))

        stateDirectory.deleteRunnerState("SIM-1", pid = 500)
        assertNull(stateDirectory.readRunnerState("SIM-1"))
    }

    @Test
    fun `an unreadable record counts as none`() {
        File(folder, "runners").mkdirs()
        File(folder, "runners/SIM-1.json").writeText("{not json")

        assertNull(stateDirectory.readRunnerState("SIM-1"))
    }
}
