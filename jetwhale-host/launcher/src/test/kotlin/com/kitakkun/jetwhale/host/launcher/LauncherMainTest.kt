package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.LauncherState
import com.kitakkun.jetwhale.host.release.hostJarName
import com.kitakkun.jetwhale.host.release.hostPlatformKey
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the launcher's `main` in a JVM of its own, with [StubHost] as the bundled host. */
class LauncherMainTest {
    private val appData: Path = Files.createTempDirectory("launcher-main")
    private val packageResources: Path = appData.resolve("package")
    private val hostDirectory = HostDirectory(appData.resolve("host"))
    private val bundledVersion = hostVersion("1.0.0-alpha13")
    private val platformKey = checkNotNull(hostPlatformKey(System.getProperty("os.name"), System.getProperty("os.arch")))

    init {
        val bundledDirectory = Files.createDirectories(packageResources.resolve("host"))
        val stubClass = "${StubHost::class.java.name.replace('.', '/')}.class"
        JarOutputStream(Files.newOutputStream(bundledDirectory.resolve(BundledHostDirectory.JAR_FILE_NAME))).use { jar ->
            jar.putNextEntry(JarEntry(stubClass))
            jar.write(checkNotNull(StubHost::class.java.classLoader.getResourceAsStream(stubClass)).use { it.readBytes() })
            jar.closeEntry()
        }
        val metadata = hostMetadata(bundledVersion.name, "unchecked".toByteArray())
        Files.writeString(
            bundledDirectory.resolve("release.json"),
            metadata.copy(
                mainClass = StubHost::class.java.name,
                jvmArgs = listOf("-Dstub.common=1"),
                platforms = mapOf(platformKey to metadata.platforms.getValue(PLATFORM).copy(jvmArgs = listOf("-Dstub.platform=2"))),
            ).encode(),
        )
    }

    @Test
    fun `logs an error it did not expect to launcher log and exits with status 1`() {
        Files.createDirectories(appData.resolve("host/launch.lock"))

        assertEquals(1, runLauncher("--headless"))
        assertContains(Files.readString(appData.resolve("logs/launcher.log")), "launch.lock")
    }

    @Test
    fun `runs the host's main in its own JVM, under a class loader that sees none of the launcher's classes`() {
        assertEquals(0, runLauncher("return", "--retry", bundledVersion.name, "--server-port", "5103"))

        val found = stubHostFindings()
        assertEquals("false", found.getProperty("kotlinVisible"))
        assertEquals("false", found.getProperty("launcherVisible"))
        assertEquals("true", found.getProperty("contextClassLoaderIsOwn"))
        assertEquals("return --server-port 5103", found.getProperty("arguments"))
        assertEquals("1", found.getProperty("jetwhale.launcher.contract"))
        assertEquals(hostDirectory.root.toString(), found.getProperty("jetwhale.launcher.hostDir"))
        assertEquals("null", found.getProperty("jetwhale.launcher.setAsideVersion"))
        assertEquals("null", found.getProperty("skiko.library.path"))
        assertEquals("1", found.getProperty("stub.common"))
        assertEquals("2", found.getProperty("stub.platform"))
        assertContains(Files.readString(hostDirectory.hostLogFile(bundledVersion)), "stub host output")
        assertEquals(LauncherState.EMPTY, hostDirectory.readLauncherState(), "a main that returns ends its JVM, which is neither a failed nor a completed start")
    }

    @Test
    fun `counts a throw from the host's main as a failed start and exits with status 1`() {
        assertEquals(1, runLauncher("throw"))

        assertEquals(mapOf(bundledVersion to 1), hostDirectory.readLauncherState().failedStartCounts)
        assertNull(hostDirectory.readLauncherState().startInProgress)
        assertContains(Files.readString(hostDirectory.hostLogFile(bundledVersion)), "stub host failed")
        assertContains(Files.readString(appData.resolve("logs/launcher.log")), "IllegalStateException: stub host failed")
    }

    @Test
    fun `counts an exit within the startup time window as neither failed nor completed`() {
        assertEquals(3, runLauncher("exit"))

        assertEquals(LauncherState.EMPTY, hostDirectory.readLauncherState())
    }

    @Test
    fun `counts a start whose JVM halted as failed at the next launch`() {
        assertEquals(134, runLauncher("halt"))
        assertEquals(bundledVersion, assertNotNull(hostDirectory.readLauncherState().startInProgress).version)

        assertEquals(0, runLauncher("return"))

        assertEquals(mapOf(bundledVersion to 1), hostDirectory.readLauncherState().failedStartCounts)
        assertNull(hostDirectory.readLauncherState().startInProgress)
    }

    @Test
    fun `skips a downloaded version that needs a runtime module its host could not see`() {
        val version = hostVersion("1.0.0-alpha14")
        val directory = Files.createDirectories(hostDirectory.root.resolve(version.name))
        val jar = Files.copy(packageResources.resolve("host/${BundledHostDirectory.JAR_FILE_NAME}"), directory.resolve(hostJarName(version, platformKey)))
        val metadata = hostMetadata(version.name, Files.readAllBytes(jar))
        Files.writeString(
            directory.resolve("release.json"),
            metadata.copy(
                mainClass = StubHost::class.java.name,
                runtime = metadata.runtime.copy(javaFeatureVersion = Runtime.version().feature(), modules = metadata.runtime.modules + "jdk.attach"),
                platforms = mapOf(platformKey to metadata.platforms.getValue(PLATFORM)),
            ).encode(),
        )

        assertEquals(0, runLauncher("return"))

        val launcherLog = Files.readString(appData.resolve("logs/launcher.log"))
        assertContains(launcherLog, "Skipping 1.0.0-alpha14: MissingModules(modules=[jdk.attach])")
        assertContains(launcherLog, "Starting 1.0.0-alpha13 (bundled)")
    }

    private fun runLauncher(vararg arguments: String): Int {
        val launcher = ProcessBuilder(
            listOf(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                "-D$APP_DATA_DIR_PROPERTY=$appData",
                "-Dcompose.application.resources.dir=$packageResources",
                "-Dskiko.library.path=$packageResources",
                "com.kitakkun.jetwhale.host.launcher.LauncherMainKt",
            ) + arguments,
        ).redirectErrorStream(true).redirectOutput(appData.resolve("launcher-output.txt").toFile()).start()

        assertTrue(launcher.waitFor(60, TimeUnit.SECONDS), "the launcher did not exit")
        return launcher.exitValue()
    }

    private fun stubHostFindings(): Properties = Properties().apply {
        Files.newInputStream(appData.resolve("stub-host.properties")).use(::load)
    }
}
