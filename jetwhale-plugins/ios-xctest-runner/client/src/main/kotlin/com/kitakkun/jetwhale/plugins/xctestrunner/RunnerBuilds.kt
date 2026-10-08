package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
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

/**
 * The runner's builds, made with the Xcode on this machine under [buildsDirectory]: once per Xcode
 * build and project content, and once more per team and device for a device, which needs a signed
 * runner. Building against the installed Xcode keeps the runner's use of XCTest's private event API
 * in step with the XCTest it runs with. A build holds a file lock, so plugins that share the
 * directory never build the same thing at once.
 */
internal class RunnerBuilds(
    private val buildsDirectory: File,
    private val runnerProjectZip: ByteArray,
    private val xcrunPath: String,
    private val commandOutputRunner: CommandOutputRunner,
    private val lockTimeout: Duration,
) {
    @Volatile
    private var xcodeBuildVersion: String? = null

    /** The `.xctestrun` file for [destination], building the runner first when there is none. */
    suspend fun xctestrunFor(destination: RunnerDestination): File {
        val projectDirectory = File(buildsDirectory, "${readXcodeBuildVersion()}-${sha256Hex(runnerProjectZip).take(16)}")
        val derivedData = File(projectDirectory, destination.buildDirectoryName)
        findXctestrun(derivedData)?.let { return it }
        return withFileLock(File(projectDirectory, "${destination.buildDirectoryName}.lock"), lockTimeout) {
            findXctestrun(derivedData) ?: build(projectDirectory, derivedData, destination)
        }
    }

    private suspend fun build(projectDirectory: File, derivedData: File, destination: RunnerDestination): File {
        val source = File(projectDirectory, "source")
        if (!source.isDirectory) withContext(Dispatchers.IO) { unpack(runnerProjectZip, source) }
        val result = commandOutputRunner.run(buildCommand(source, derivedData, destination))
        if (result.exitCode != 0) throw XcTestRunnerStartException(XcodebuildFailures.reasonOf(result.text, destination), null)
        return findXctestrun(derivedData) ?: throw XcTestRunnerStartException("xcodebuild built the XCTest runner but wrote no .xctestrun under $derivedData", null)
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
        xcodeBuildVersion?.let { return it }
        val result = commandOutputRunner.run(listOf(xcrunPath, "xcodebuild", "-version"))
        if (result.exitCode != 0) throw XcTestRunnerStartException("the XCTest runner needs Xcode, and 'xcodebuild -version' failed: ${result.text.trim().take(300)}", null)
        val version = Regex("""Build version (\S+)""").find(result.text)?.groupValues?.get(1)
            ?: throw XcTestRunnerStartException("'xcodebuild -version' printed no build version: ${result.text.trim().take(200)}", null)
        return version.also { xcodeBuildVersion = it }
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
