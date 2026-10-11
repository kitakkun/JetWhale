package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IosSimulatorDeviceControllerTest {
    private val folder: File = Files.createTempDirectory("ios-simulator").toFile()
    private val idbCalls = File(folder, "idb-calls")
    private val startedCompanions = mutableListOf<FakeCompanionProcess>()
    private val idleTimeout = 3.minutes

    /** An idb stand-in for a 402x874-point simulator at 3x that notes each call. */
    private val fakeIdb = File(folder, "idb").apply {
        writeText(
            """
            #!/bin/sh
            echo "${'$'}*" >> '${idbCalls.path}'
            case "${'$'}1" in
              describe) echo '{"screen_dimensions":{"width":1206,"height":2622,"density":3}}' ;;
            esac
            """.trimIndent() + "\n",
        )
        setExecutable(true)
    }

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun `a simulator's idb calls go through a companion started for it, which stops once nothing uses it`() = runTest {
        assumeShellScriptsLaunch()
        val simulator = IosSimulatorDeviceController(udid = "SIM-1", xcrunPath = "xcrun", idbPath = fakeIdb.path, companions = companions())

        simulator.tap(x = 603, y = 1311)

        assertEquals(listOf("idb_companion", "--udid", "SIM-1", "--grpc-port", "10000"), startedCompanions.single().command)
        assertEquals(listOf("describe --udid SIM-1 --json", "ui tap --udid SIM-1 201 437"), idbCalls.readLines())
        delay(idleTimeout + 1.seconds)
        assertTrue(startedCompanions.single().destroyed)
    }

    @Test
    fun `a simulator's live stream holds its companion until the mirror releases the simulator`() = runTest {
        assumeShellScriptsLaunch()
        val simulator = IosSimulatorDeviceController(udid = "SIM-1", xcrunPath = "xcrun", idbPath = fakeIdb.path, companions = companions())

        simulator.openVideoStream(wanted = null)
        delay(idleTimeout * 2)
        assertFalse(startedCompanions.single().destroyed)

        simulator.release()
        delay(idleTimeout + 1.seconds)
        assertTrue(startedCompanions.single().destroyed)
    }

    private fun TestScope.companions() = IdbCompanions(
        idbCompanionPath = "idb_companion",
        idbPath = "idb",
        launcher = { command -> FakeCompanionProcess(command, readyLine = """{"grpc_port":${command.last()}}""").also(startedCompanions::add) },
        commands = { },
        ports = { 10_000 },
        idleTimeout = idleTimeout,
        scope = backgroundScope,
    )
}
