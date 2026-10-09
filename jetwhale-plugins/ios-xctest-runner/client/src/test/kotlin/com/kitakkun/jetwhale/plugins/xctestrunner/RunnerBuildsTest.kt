package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class RunnerBuildsTest {
    private val buildsDirectory: File = Files.createTempDirectory("runner-builds").toFile()
    private val xcodebuildCommands = FakeXcodebuildCommands()

    @AfterTest
    fun deleteBuildsDirectory() {
        buildsDirectory.deleteRecursively()
    }

    @Test
    fun `the runner is built once for every simulator and then reused`() = runBlocking {
        val builds = builds()

        val first = builds.xctestrunFor(RunnerDestination.Simulator("SIM-1"))
        val second = builds.xctestrunFor(RunnerDestination.Simulator("SIM-2"))

        assertEquals(first, second)
        assertTrue(first.isFile)
        val buildCommand = xcodebuildCommands.buildCommands.single()
        assertEquals(listOf("xcrun", "xcodebuild", "build-for-testing", "-project"), buildCommand.take(4))
        assertTrue(buildCommand[4].endsWith("source/JetWhaleRunner.xcodeproj"))
        assertEquals(listOf("-destination", "generic/platform=iOS Simulator"), buildCommand.takeLast(2))
    }

    @Test
    fun `the project is unpacked where the build reads it`() = runBlocking {
        builds().xctestrunFor(RunnerDestination.Simulator("SIM-1"))

        val project = File(xcodebuildCommands.buildCommands.single()[4])
        assertEquals("// project", File(project, "project.pbxproj").readText())
    }

    @Test
    fun `an Xcode installed while the host runs gets a build of its own`() = runBlocking {
        val builds = builds()
        builds.xctestrunFor(RunnerDestination.Simulator("SIM-1"))
        xcodebuildCommands.buildVersion = "27B100"

        builds.xctestrunFor(RunnerDestination.Simulator("SIM-1"))

        assertEquals(2, xcodebuildCommands.buildCommands.size)
    }

    @Test
    fun `an xctestrun that no completed build left is built again`() = runBlocking {
        val builds = builds()
        val first = builds.xctestrunFor(RunnerDestination.Simulator("SIM-1"))
        File(first.parentFile.parentFile.parentFile, "build-complete").delete()

        builds.xctestrunFor(RunnerDestination.Simulator("SIM-1"))

        assertEquals(2, xcodebuildCommands.buildCommands.size)
    }

    @Test
    fun `a build deletes builds for another Xcode, and another project's once unused for a week`() = runBlocking {
        val otherXcodeProject = createProjectDirectory("26E100-0000000000000000", lastUsed = NOW)
        val recentProject = createProjectDirectory("27A266a-1111111111111111", lastUsed = NOW.minus(Duration.ofDays(6)))
        val unusedProject = createProjectDirectory("27A266a-2222222222222222", lastUsed = NOW.minus(Duration.ofDays(8)))

        builds().xctestrunFor(RunnerDestination.Simulator("SIM-1"))

        assertFalse(otherXcodeProject.exists())
        assertTrue(recentProject.exists())
        assertFalse(unusedProject.exists())
    }

    @Test
    fun `a stale build another plugin holds locked is left for later`() = runBlocking {
        val otherXcodeProject = createProjectDirectory("26E100-0000000000000000", lastUsed = NOW)
        RandomAccessFile(File(otherXcodeProject, "simulator.lock"), "rw").channel.use { channel ->
            channel.lock().use {
                builds().xctestrunFor(RunnerDestination.Simulator("SIM-1"))
            }
        }

        assertTrue(otherXcodeProject.exists())
    }

    @Test
    fun `a changed runner project builds the runner again`() = runBlocking {
        builds().xctestrunFor(RunnerDestination.Simulator("SIM-1"))

        builds(runnerProjectZip(mapOf("JetWhaleRunner.xcodeproj/project.pbxproj" to "// changed"))).xctestrunFor(RunnerDestination.Simulator("SIM-1"))

        assertEquals(2, xcodebuildCommands.buildCommands.size)
    }

    @Test
    fun `a device's runner is signed for its team, with bundle IDs of the team's own, for that device`() = runBlocking {
        builds().xctestrunFor(RunnerDestination.Device("00008110", developmentTeam = "ABCDE12345"))

        val buildCommand = xcodebuildCommands.buildCommands.single()
        assertEquals(
            listOf(
                "-destination",
                "id=00008110",
                "-allowProvisioningUpdates",
                "-allowProvisioningDeviceRegistration",
                "DEVELOPMENT_TEAM=ABCDE12345",
                "JETWHALE_RUNNER_BUNDLE_ID_PREFIX=com.kitakkun.jetwhale.xctestrunner.abcde12345",
            ),
            buildCommand.takeLast(6),
        )
    }

    @Test
    fun `a failed build says what to do about it and is tried again next time`() = runBlocking {
        xcodebuildCommands.buildFailure = "error: No Account for Team \"ABCDE12345\". Add a new account in Accounts settings.\n** TEST BUILD FAILED **"
        val device = RunnerDestination.Device("00008110", developmentTeam = "ABCDE12345")

        val failure = assertFailsWith<XcTestRunnerStartException> { builds().xctestrunFor(device) }
        assertTrue(failure.message.orEmpty().startsWith("the XCTest runner could not be signed for team ABCDE12345"))

        xcodebuildCommands.buildFailure = null
        builds().xctestrunFor(device)
        assertEquals(2, xcodebuildCommands.buildCommands.size)
    }

    @Test
    fun `a project entry that points outside the project is refused`() = runBlocking {
        val failure = assertFailsWith<XcTestRunnerStartException> { builds(runnerProjectZip(mapOf("../escaped.txt" to "x"))).xctestrunFor(RunnerDestination.Simulator("SIM-1")) }

        assertEquals("the XCTest runner's project has an entry outside it: ../escaped.txt", failure.message)
        assertFalse(File(buildsDirectory.parentFile, "escaped.txt").exists())
        assertTrue(xcodebuildCommands.buildCommands.isEmpty())
    }

    private fun builds(zip: ByteArray = runnerProjectZip()) = RunnerBuilds(
        buildsDirectory = buildsDirectory,
        runnerProjectZip = zip,
        xcrunPath = "xcrun",
        commandOutputRunner = xcodebuildCommands,
        lockTimeout = 1.minutes,
        unusedBuildLifetime = 7.days,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    /** A project directory another client left, last used at [lastUsed], with a build lock in it. */
    private fun createProjectDirectory(directoryName: String, lastUsed: Instant): File = File(buildsDirectory, directoryName).apply {
        mkdirs()
        File(this, "simulator.lock").createNewFile()
        File(this, "last-used").apply {
            createNewFile()
            setLastModified(lastUsed.toEpochMilli())
        }
    }
}

private val NOW: Instant = Instant.parse("2026-10-09T00:00:00Z")
