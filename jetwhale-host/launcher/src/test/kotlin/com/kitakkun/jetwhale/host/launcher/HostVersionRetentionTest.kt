package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.hostJarName
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class HostVersionRetentionTest {
    private val hostDirectory = HostDirectory(Files.createTempDirectory("host-version-retention").resolve("host"))
    private val retention = HostVersionRetention(hostDirectory, bundledHostVersion = hostVersion("1.0.0-alpha13"), log = {})

    @Test
    fun `keeps the running version and the newest newer one, and forgets the versions it deletes`() {
        listOf("1.0.0-alpha14", "1.0.0-alpha15", "1.0.0-alpha16", "1.0.0-alpha17").forEach { writeHostVersion(hostDirectory.root.resolve(it), it) { metadata -> metadata } }
        val launcherState = LauncherState(
            completedStartVersions = hostVersions("1.0.0-alpha13", "1.0.0-alpha14"),
            setAsideVersions = hostVersions("1.0.0-alpha15", "1.0.0-alpha16", "1.0.0-alpha17"),
            failedStartCounts = mapOf(hostVersion("1.0.0-alpha16") to 2, hostVersion("1.0.0-alpha17") to 2),
            startedHostProcess = null,
        )

        val prunedState = retention.pruneAfterCompletedStart(downloadedHostJar("1.0.0-alpha14"), launcherState)

        assertEquals(listOf("1.0.0-alpha17", "1.0.0-alpha14"), hostDirectory.hostVersionDirectories().map { it.version.name })
        assertEquals(
            LauncherState(
                completedStartVersions = hostVersions("1.0.0-alpha13", "1.0.0-alpha14"),
                setAsideVersions = hostVersions("1.0.0-alpha17"),
                failedStartCounts = mapOf(hostVersion("1.0.0-alpha17") to 2),
                startedHostProcess = null,
            ),
            prunedState,
        )
    }

    private fun downloadedHostJar(versionName: String): ChosenHostJar {
        val directory = hostDirectory.root.resolve(versionName)
        return ChosenHostJar(
            version = hostVersion(versionName),
            metadata = hostMetadata(versionName, Files.readAllBytes(directory.resolve(hostJarName(hostVersion(versionName), PLATFORM)))),
            path = directory.resolve(hostJarName(hostVersion(versionName), PLATFORM)),
            isBundled = false,
        )
    }
}
