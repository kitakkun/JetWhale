package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.security.MessageDigest
import java.time.Clock
import java.util.zip.ZipInputStream
import kotlin.time.Duration

/** The scheme the runner's project builds, and the test the runner serves from. */
internal const val RUNNER_SCHEME = "JetWhaleRunner"

internal const val RUNNER_TEST_IDENTIFIER = "JetWhaleRunnerUITests/RunnerTests/testServe"

/** The runner's bundle IDs start with this; a device build appends the team, since an ID belongs to one team. */
private const val RUNNER_BUNDLE_ID_PREFIX = "com.kitakkun.jetwhale.xctestrunner"

/** A target together with what its runner is built and started for. */
internal sealed interface RunnerDestination {
    val udid: String

    /** The team a device's runner is signed for; null for a simulator, which needs none. */
    val developmentTeam: String?

    /** The build this destination runs, under the builds directory. */
    val buildDirectoryName: String

    data class Simulator(override val udid: String) : RunnerDestination {
        override val developmentTeam: String? get() = null

        override val buildDirectoryName: String get() = "simulator"
    }

    data class Device(override val udid: String, override val developmentTeam: String) : RunnerDestination {
        override val buildDirectoryName: String get() = "device-$developmentTeam-$udid"
    }
}

/** A file each build writes once xcodebuild has finished it; an `.xctestrun` alone may be half-built. */
private const val BUILD_COMPLETE_MARKER = "build-complete"

/** Touched on every lookup, so a build other clients still use is not pruned. */
private const val LAST_USED_MARKER = "last-used"

/**
 * The runner's builds, made with the Xcode on this machine under [buildsDirectory]: once per Xcode
 * build and project content, and once more per team and device for a device, which needs a signed
 * runner. Building against the installed Xcode keeps the runner's use of XCTest's private event API
 * in step with the XCTest it runs with, so Xcode's build version is read again on every lookup.
 *
 * A build holds a file lock, so plugins that share the directory never build the same thing at
 * once, and a build is used only once it is complete. After a build, builds for another Xcode are
 * deleted, and so are those of another project unused for [unusedBuildLifetime]: another plugin may
 * bundle a newer or older client, and alternating between them should not rebuild every time.
 */
internal class RunnerBuilds(
    private val buildsDirectory: File,
    private val runnerProjectZip: ByteArray,
    private val xcrunPath: String,
    private val commandOutputRunner: CommandOutputRunner,
    private val lockTimeout: Duration,
    private val unusedBuildLifetime: Duration,
    private val clock: Clock,
) {
    /** The `.xctestrun` file for [destination], building the runner first when there is no complete build. */
    suspend fun xctestrunFor(destination: RunnerDestination): File {
        val xcodeBuildVersion = readXcodeBuildVersion()
        val projectDirectory = File(buildsDirectory, "$xcodeBuildVersion-${sha256Hex(runnerProjectZip).take(16)}")
        val derivedData = File(projectDirectory, destination.buildDirectoryName)
        return withFileLock(File(projectDirectory, "${destination.buildDirectoryName}.lock"), lockTimeout) {
            File(projectDirectory, LAST_USED_MARKER).apply {
                createNewFile()
                setLastModified(clock.millis())
            }
            findCompleteXctestrun(derivedData) ?: build(projectDirectory, derivedData, destination).also { deleteStaleBuilds(projectDirectory, xcodeBuildVersion) }
        }
    }

    private suspend fun build(projectDirectory: File, derivedData: File, destination: RunnerDestination): File {
        val source = File(projectDirectory, "source")
        if (!source.isDirectory) withContext(Dispatchers.IO) { unpack(runnerProjectZip, source) }
        File(derivedData, BUILD_COMPLETE_MARKER).delete()
        val result = commandOutputRunner.run(buildCommand(source, derivedData, destination))
        if (result.exitCode != 0) throw XcTestRunnerStartException(XcodebuildFailures.reasonOf(result.text, destination), null)
        val xctestrun = findXctestrun(derivedData) ?: throw XcTestRunnerStartException("xcodebuild built the XCTest runner but wrote no .xctestrun under $derivedData", null)
        File(derivedData, BUILD_COMPLETE_MARKER).createNewFile()
        return xctestrun
    }

    /**
     * Deletes the project directories beside [currentProjectDirectory] that are built with another Xcode than
     * [xcodeBuildVersion], or that nothing has used for [unusedBuildLifetime]. A directory whose
     * builds another plugin holds locked is left for a later pass.
     */
    private fun deleteStaleBuilds(currentProjectDirectory: File, xcodeBuildVersion: String) {
        val now = clock.millis()
        buildsDirectory.listFiles(File::isDirectory).orEmpty().filter { it != currentProjectDirectory }.forEach { projectDirectory ->
            val lastUsedMillis = File(projectDirectory, LAST_USED_MARKER).takeIf(File::isFile)?.lastModified() ?: projectDirectory.lastModified()
            val stale = !projectDirectory.name.startsWith("$xcodeBuildVersion-") || now - lastUsedMillis > unusedBuildLifetime.inWholeMilliseconds
            if (stale) deleteUnlessLocked(projectDirectory)
        }
    }
    private fun buildCommand(source: File, derivedData: File, destination: RunnerDestination): List<String> {
        val common = listOf(
            xcrunPath, "xcodebuild", "build-for-testing",
            "-project", File(source, "$RUNNER_SCHEME.xcodeproj").path,
            "-scheme", RUNNER_SCHEME,
            "-configuration", "Debug",
            "-derivedDataPath", derivedData.path,
        )
        return when (destination) {
            is RunnerDestination.Simulator -> common + listOf("-destination", "generic/platform=iOS Simulator")

            is RunnerDestination.Device -> common + listOf(
                "-destination",
                "id=${destination.udid}",
                "-allowProvisioningUpdates",
                "-allowProvisioningDeviceRegistration",
                "DEVELOPMENT_TEAM=${destination.developmentTeam}",
                "JETWHALE_RUNNER_BUNDLE_ID_PREFIX=$RUNNER_BUNDLE_ID_PREFIX.${destination.developmentTeam.lowercase()}",
            )
        }
    }

    private suspend fun readXcodeBuildVersion(): String {
        val result = commandOutputRunner.run(listOf(xcrunPath, "xcodebuild", "-version"))
        if (result.exitCode != 0) throw XcTestRunnerStartException("the XCTest runner needs Xcode, and 'xcodebuild -version' failed: ${result.text.trim().take(300)}", null)
        val version = Regex("""Build version (\S+)""").find(result.text)?.groupValues?.get(1)
            ?: throw XcTestRunnerStartException("'xcodebuild -version' printed no build version: ${result.text.trim().take(200)}", null)
        return version
    }
}

/** The runner's `.xctestrun` under [derivedData], once its build has completed. */
private fun findCompleteXctestrun(derivedData: File): File? = findXctestrun(derivedData)?.takeIf { File(derivedData, BUILD_COMPLETE_MARKER).isFile }

/** Deletes [projectDirectory] when no one holds any of its builds' locks, holding them all meanwhile. */
private fun deleteUnlessLocked(projectDirectory: File) {
    val channels = projectDirectory.listFiles { file -> file.extension == "lock" }.orEmpty().map { RandomAccessFile(it, "rw").channel }
    try {
        val locks = channels.map { channel ->
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
        }
        if (locks.all { it != null }) projectDirectory.deleteRecursively()
    } finally {
        channels.forEach(FileChannel::close)
    }
}

/** The runner's `.xctestrun` under [derivedData], or null before it is built. */
private fun findXctestrun(derivedData: File): File? = File(derivedData, "Build/Products").listFiles { file -> file.extension == "xctestrun" }?.firstOrNull()

/** Unpacks [zip] into [directory] through a staging directory, so a half-written unpack is never used. */
private fun unpack(zip: ByteArray, directory: File) {
    val staging = File(directory.parentFile, "${directory.name}.partial").apply {
        deleteRecursively()
        mkdirs()
    }
    ZipInputStream(ByteArrayInputStream(zip)).use { zipInput ->
        generateSequence { zipInput.nextEntry }.forEach { entry ->
            val entryFile = File(staging, entry.name).canonicalFile
            if (!entryFile.path.startsWith(staging.canonicalPath + File.separator)) throw XcTestRunnerStartException("the XCTest runner's project has an entry outside it: ${entry.name}", null)
            if (entry.isDirectory) {
                entryFile.mkdirs()
            } else {
                entryFile.parentFile.mkdirs()
                entryFile.outputStream().use(zipInput::copyTo)
            }
        }
    }
    if (!staging.renameTo(directory)) throw XcTestRunnerStartException("could not move the XCTest runner's project into $directory", null)
}

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
