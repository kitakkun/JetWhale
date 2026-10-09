package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration

/**
 * The iPhone capture helper, compiled from [source] by the Swift compiler on this Mac and kept under
 * [buildsDirectory]: one build per compiler and source. The compiler is told apart by what
 * `swiftc --version` prints, which changes with every Xcode, so a new Xcode builds the helper again,
 * and so does a version of this plugin whose helper source differs.
 *
 * A build is compiled into a directory of its own and moved into place whole, so a half-built
 * helper is never run, and two hosts building at once both end up running one complete build.
 * After a build, the builds of another compiler are deleted, and so are those of another source
 * unused for [unusedBuildLifetime]: another version of this plugin may still run them.
 *
 * [runCommand] runs a command to completion; tests stand it in for the compiler.
 */
internal class IphoneCaptureHelperBuilds(
    private val buildsDirectory: File,
    private val source: ByteArray,
    private val xcrunPath: String,
    private val unusedBuildLifetime: Duration,
    private val clock: Clock,
    private val runCommand: suspend (List<String>) -> CommandResult,
) {
    /**
     * The helper's executable, compiled first when this compiler has not compiled this source yet,
     * which takes a few seconds.
     *
     * @throws DeviceControlException when the Swift compiler is missing or fails.
     */
    suspend fun findOrBuildHelperExecutable(): File = withContext(Dispatchers.IO) {
        val compilerVersionResult = runCommand(listOf(xcrunPath, "swiftc", "--version"))
        if (compilerVersionResult.exitCode != 0) throw deviceControlError("mirroring an iPhone builds a helper with Xcode's Swift compiler, and 'xcrun swiftc --version' failed: ${outputOf(compilerVersionResult)}")
        val compilerKey = sha256Hex(compilerVersionResult.stdout).take(COMPILER_KEY_LENGTH)
        val buildDirectory = File(buildsDirectory, "$compilerKey-${sha256Hex(source).take(SOURCE_KEY_LENGTH)}")
        val executable = File(buildDirectory, HELPER_EXECUTABLE_FILE_NAME)
        if (!executable.canExecute()) {
            compileInto(buildDirectory)
            deleteStaleBuilds(compilerKey, buildDirectory)
        }
        File(buildDirectory, LAST_USED_MARKER).apply {
            createNewFile()
            setLastModified(clock.millis())
        }
        executable
    }

    private suspend fun compileInto(buildDirectory: File) {
        val staging = File(buildsDirectory, "${buildDirectory.name}.partial-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val sourceFile = File(staging, HELPER_SOURCE_FILE_NAME).apply { writeBytes(source) }
            val compileResult = runCommand(listOf(xcrunPath, "swiftc", "-O", "-parse-as-library", "-swift-version", "5", "-o", File(staging, HELPER_EXECUTABLE_FILE_NAME).path, sourceFile.path))
            if (compileResult.exitCode != 0) throw deviceControlError("the iPhone capture helper did not compile with this Mac's Swift compiler: ${outputOf(compileResult)}")
            // A directory left without its executable, by hand or by a crash, would refuse the move.
            if (buildDirectory.isDirectory && !File(buildDirectory, HELPER_EXECUTABLE_FILE_NAME).canExecute()) buildDirectory.deleteRecursively()
            // A host that finished the same build first has already moved its own into place.
            if (!staging.renameTo(buildDirectory) && !File(buildDirectory, HELPER_EXECUTABLE_FILE_NAME).canExecute()) {
                throw deviceControlError("could not move the iPhone capture helper into $buildDirectory")
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun deleteStaleBuilds(compilerKey: String, currentBuildDirectory: File) {
        val now = clock.millis()
        buildsDirectory.listFiles(File::isDirectory).orEmpty().filter { it != currentBuildDirectory }.forEach { buildDirectory ->
            val lastUsedMillis = File(buildDirectory, LAST_USED_MARKER).takeIf(File::isFile)?.lastModified() ?: buildDirectory.lastModified()
            if (!buildDirectory.name.startsWith("$compilerKey-") || now - lastUsedMillis > unusedBuildLifetime.inWholeMilliseconds) buildDirectory.deleteRecursively()
        }
    }
}

/** The helper's source, as the plugin bundles it beside this class. */
internal fun readIphoneCaptureHelperSource(): ByteArray = checkNotNull(IphoneCaptureHelperBuilds::class.java.getResourceAsStream(HELPER_SOURCE_FILE_NAME)) { "the plugin bundles no $HELPER_SOURCE_FILE_NAME" }.use { it.readBytes() }

private fun outputOf(result: CommandResult): String = result.stderr.ifBlank { result.stdoutText }.trim().takeLast(MAX_OUTPUT_CHARS)

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private const val HELPER_SOURCE_FILE_NAME = "IphoneScreenCapture.swift"

private const val HELPER_EXECUTABLE_FILE_NAME = "jetwhale-iphone-capture"

/** Touched on every lookup, so a build another version of the plugin still runs is not deleted. */
private const val LAST_USED_MARKER = "last-used"

private const val COMPILER_KEY_LENGTH = 12

private const val SOURCE_KEY_LENGTH = 16

/** Compiler errors end with the ones that matter. */
private const val MAX_OUTPUT_CHARS = 800
