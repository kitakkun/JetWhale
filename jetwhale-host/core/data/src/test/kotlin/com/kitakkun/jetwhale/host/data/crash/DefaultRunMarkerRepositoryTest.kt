package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.RunMarker
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultRunMarkerRepositoryTest {
    private val appDataDir: File = Files.createTempDirectory("jetwhale-app-data").toFile()
    private val previousAppDataDir = System.getProperty("jetwhale.appDataDir")

    init {
        System.setProperty("jetwhale.appDataDir", appDataDir.path)
    }

    private val markerDirectory = File(appDataDir, "run-markers")
    private val repository = DefaultRunMarkerRepository(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())))

    @AfterTest
    fun restore() {
        if (previousAppDataDir == null) System.clearProperty("jetwhale.appDataDir") else System.setProperty("jetwhale.appDataDir", previousAppDataDir)
        appDataDir.deleteRecursively()
    }

    @Test
    fun `each run keeps its own marker and deletes only that one`() {
        repository.write(marker("run-a"))
        repository.write(marker("run-b"))

        repository.delete("run-a")

        assertEquals(listOf(marker("run-b")), repository.readAll())
    }

    @Test
    fun `a rewrite replaces the marker and leaves no temporary file behind`() {
        repository.write(marker("run-a"))

        repository.write(marker("run-a").copy(startupCompleted = true))

        assertEquals(listOf(marker("run-a").copy(startupCompleted = true)), repository.readAll())
        assertEquals(listOf("run-a.properties"), markerDirectory.list()?.toList())
    }

    @Test
    fun `a truncated marker is skipped and removed without hiding the others`() {
        repository.write(marker("run-a"))
        File(markerDirectory, "run-b.properties").writeText("#JetWhale run marker\nstartedAtMillis=12")

        assertEquals(listOf(marker("run-a")), repository.readAll())
        assertFalse(File(markerDirectory, "run-b.properties").exists())
    }

    @Test
    fun `a temporary file an interrupted write left long ago is removed and a fresh one is kept`() {
        markerDirectory.mkdirs()
        val abandoned = File(markerDirectory, "run-a.tmp").apply {
            writeText("pid=")
            setLastModified(System.currentTimeMillis() - ABANDONED_AGE_MILLIS)
        }
        val inFlight = File(markerDirectory, "run-b.tmp").apply { writeText("pid=") }

        assertEquals(emptyList(), repository.readAll())
        assertFalse(abandoned.exists())
        assertTrue(inFlight.exists())
    }

    private fun marker(runId: String) = RunMarker(
        runId = runId,
        pid = 42,
        startedAtMillis = 1_000,
        workingDirectory = "/work",
        startupCompleted = false,
        consecutiveStartupCrashes = 1,
    )

    private companion object {
        const val ABANDONED_AGE_MILLIS = 10 * 60 * 1_000L
    }
}
