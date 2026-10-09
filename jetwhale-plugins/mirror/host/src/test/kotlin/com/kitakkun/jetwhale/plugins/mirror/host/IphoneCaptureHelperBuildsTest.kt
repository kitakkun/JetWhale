package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class IphoneCaptureHelperBuildsTest {
    private val buildsDirectory: File = Files.createTempDirectory("mirror-iphone-capture-builds").toFile()
    private val commands = mutableListOf<List<String>>()
    private var compilerVersion = "Apple Swift version 6.4 (swiftlang-6.4.0.34.1)"
    private var compileFails = false
    private var now = Instant.parse("2026-10-09T00:00:00Z")

    @AfterTest
    fun cleanUp() {
        buildsDirectory.deleteRecursively()
    }

    @Test
    fun `the helper is compiled once for a compiler and a source, then found`() = runBlocking {
        val builds = builds(source = "first")

        val built = builds.findOrBuildHelperExecutable()
        val found = builds.findOrBuildHelperExecutable()

        assertEquals(built, found)
        assertTrue(found.canExecute())
        assertEquals(1, commands.count(::isCompile))
    }

    @Test
    fun `the helper is compiled as a library with an entry point, optimized, in Swift 5 mode`() = runBlocking {
        val executable = builds(source = "first").findOrBuildHelperExecutable()

        val compile = commands.single(::isCompile)
        assertEquals(listOf("/usr/bin/xcrun", "swiftc", "-O", "-parse-as-library", "-swift-version", "5", "-o"), compile.take(7))
        assertEquals("IphoneScreenCapture.swift", File(compile.last()).name)
        assertEquals("jetwhale-iphone-capture", executable.name)
    }

    @Test
    fun `a new compiler compiles the helper again and deletes the old compiler's build`() = runBlocking {
        val old = builds(source = "first").findOrBuildHelperExecutable()

        compilerVersion = "Apple Swift version 6.5 (swiftlang-6.5.0.1.1)"
        val new = builds(source = "first").findOrBuildHelperExecutable()

        assertTrue(new.canExecute())
        assertFalse(old.parentFile.exists())
    }

    @Test
    fun `another source's build is kept until it has gone unused for its lifetime`() = runBlocking {
        val other = builds(source = "first").findOrBuildHelperExecutable()

        builds(source = "second").findOrBuildHelperExecutable()
        assertTrue(other.canExecute())

        now = now.plusSeconds(31.days.inWholeSeconds)
        builds(source = "third").findOrBuildHelperExecutable()
        assertFalse(other.parentFile.exists())
    }

    @Test
    fun `a failed compile says what the compiler printed and leaves no build behind`() = runBlocking {
        compileFails = true

        val failure = assertFailsWith<DeviceControlException> { builds(source = "first").findOrBuildHelperExecutable() }

        assertContains(failure.message.orEmpty(), "error: cannot find 'AVCaptureDevice' in scope")
        assertEquals(emptyList(), buildsDirectory.listFiles().orEmpty().toList())
    }

    @Test
    fun `a build whose executable is gone is compiled again`() = runBlocking {
        val executable = builds(source = "first").findOrBuildHelperExecutable()
        executable.delete()

        assertTrue(builds(source = "first").findOrBuildHelperExecutable().canExecute())
        assertEquals(2, commands.count(::isCompile))
    }

    @Test
    fun `without a working Swift compiler the failure says so`() = runBlocking {
        val builds = IphoneCaptureHelperBuilds(buildsDirectory, "first".toByteArray(), "/usr/bin/xcrun", unusedBuildLifetime = 30.days, clock = Clock.fixed(now, ZoneOffset.UTC)) {
            CommandResult(exitCode = 1, stdout = ByteArray(0), stderr = "xcrun: error: invalid active developer path")
        }

        val failure = assertFailsWith<DeviceControlException> { builds.findOrBuildHelperExecutable() }

        assertContains(failure.message.orEmpty(), "invalid active developer path")
    }

    private fun builds(source: String) = IphoneCaptureHelperBuilds(buildsDirectory, source.toByteArray(), "/usr/bin/xcrun", unusedBuildLifetime = 30.days, clock = Clock.fixed(now, ZoneOffset.UTC), runCommand = ::runFakeCommand)

    /** Stands in for swiftc: prints [compilerVersion], or writes the executable it is asked for. */
    private fun runFakeCommand(command: List<String>): CommandResult {
        commands += command
        if ("--version" in command) return CommandResult(exitCode = 0, stdout = compilerVersion.toByteArray(), stderr = "")
        if (compileFails) return CommandResult(exitCode = 1, stdout = ByteArray(0), stderr = "IphoneScreenCapture.swift:1:1: error: cannot find 'AVCaptureDevice' in scope")
        File(command[command.indexOf("-o") + 1]).apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        return CommandResult(exitCode = 0, stdout = ByteArray(0), stderr = "")
    }
}

private fun isCompile(command: List<String>): Boolean = "-o" in command
