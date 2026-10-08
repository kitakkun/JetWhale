package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun runnerStatus(protocolVersion: Int = PROTOCOL_VERSION) = RunnerStatus(protocolVersion = protocolVersion, screenWidthPixels = 1206, screenHeightPixels = 2622, scale = 3.0, eventSynthesis = true)

/** A runner's endpoint that notes the commands it is sent, and answers its status once [answering]. */
internal class FakeRunnerConnection(var answering: Boolean, var protocolVersion: Int = PROTOCOL_VERSION) : RunnerConnection {
    val sent = mutableListOf<Pair<String, JsonObject>>()

    /** Set to make every command fail as if the runner had gone away. */
    var unreachable = false

    /** Set to make every command fail as if the runner had stopped answering after taking it. */
    var stopsAnsweringMidCommand = false

    /** What the runner does when told to shut down, besides noting it. */
    var onShutdown: () -> Unit = {}

    /** Set to make the status fail the way a runner that answers but cannot report one does. */
    var statusFailure: String? = null

    /** The answer to each command by path, besides `ok`; an empty one for a path not listed. */
    val answers = mutableMapOf<String, JsonObject>()

    override suspend fun status(): RunnerStatus {
        if (!answering || unreachable) throw RunnerUnreachableException("not listening", null)
        statusFailure?.let { throw XcTestRunnerException(it, null) }
        return runnerStatus(protocolVersion)
    }

    override suspend fun send(path: String, body: JsonObject): JsonObject {
        if (unreachable) throw RunnerUnreachableException("connection refused", null)
        sent += path to body
        if (stopsAnsweringMidCommand) throw XcTestRunnerException("the XCTest runner stopped answering during ${path.removePrefix("/")}", null)
        if (path == "/shutdown") onShutdown()
        return answers[path] ?: JsonObject(emptyMap())
    }

    val paths: List<String> get() = sent.map { it.first }
}

/**
 * An `xcodebuild` or `iproxy` stand-in with [pid] that prints [output] and then runs until destroyed,
 * or, with [exitsAtOnce], exits after printing it, as an `xcodebuild` whose test could not start does.
 */
internal class FakeToolProcess(val command: List<String>, private val pid: Long, output: String, exitsAtOnce: Boolean) : Process() {
    private val pipe = PipedOutputStream()
    private val stdout = PipedInputStream(pipe, 1 shl 16)
    private val exitFuture = CompletableFuture<Process>()

    var destroyed = false
        private set

    private var exited = exitsAtOnce

    init {
        pipe.write(output.toByteArray())
        pipe.flush()
        if (exitsAtOnce) {
            pipe.close()
            exitFuture.complete(this)
        }
    }

    /** Ends the process on its own, the way xcodebuild exits when the runner's test ends. */
    fun exit() {
        exited = true
        pipe.close()
        exitFuture.complete(this)
    }

    override fun pid(): Long = pid

    override fun onExit(): CompletableFuture<Process> = exitFuture

    override fun isAlive(): Boolean = !destroyed && !exited

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun waitFor(): Int = if (isAlive) error("a fake process never exits by itself") else 0

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !isAlive

    override fun exitValue(): Int = if (isAlive) throw IllegalThreadStateException("still running") else 0

    override fun destroy() {
        destroyed = true
        pipe.close()
        exitFuture.complete(this)
    }

    override fun destroyForcibly(): Process = apply { destroy() }
}

/**
 * An `xcrun xcodebuild` stand-in for builds: it reports Xcode [buildVersion], and a build writes an
 * `.xctestrun` where xcodebuild would, or fails with [buildFailure] when that is set.
 */
internal class FakeXcodebuildCommands(var buildVersion: String = "27A266a", var buildFailure: String? = null) : CommandOutputRunner {
    val commands = mutableListOf<List<String>>()

    val buildCommands: List<List<String>> get() = commands.filter { "build-for-testing" in it }

    override suspend fun run(command: List<String>): CommandOutput {
        commands += command
        return when {
            "-version" in command -> CommandOutput(exitCode = 0, text = "Xcode 27.0\nBuild version $buildVersion\n")

            "build-for-testing" in command -> buildFailure?.let { CommandOutput(exitCode = 65, text = it) } ?: run {
                val products = File(command[command.indexOf("-derivedDataPath") + 1], "Build/Products").apply { mkdirs() }
                File(products, "JetWhaleRunner_iphonesimulator27.0-arm64.xctestrun").writeText("<plist/>")
                CommandOutput(exitCode = 0, text = "")
            }

            else -> error("unexpected command $command")
        }
    }
}

/** A zip of a stand-in runner project with the given [entries], by path. */
internal fun runnerProjectZip(entries: Map<String, String> = mapOf("JetWhaleRunner.xcodeproj/project.pbxproj" to "// project")): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        entries.forEach { (path, text) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}
