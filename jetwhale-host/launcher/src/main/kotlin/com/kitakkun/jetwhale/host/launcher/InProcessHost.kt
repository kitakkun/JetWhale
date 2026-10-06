package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.LauncherContract
import com.kitakkun.jetwhale.host.release.systemPropertyOf
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files

/**
 * Runs a host version in this JVM, on the calling thread. The launcher's process is then the app the
 * OS knows, with the app's identity and permissions, rather than a `java` process of its own.
 *
 * The host's jar gets a class loader whose parent is the platform class loader, so none of the
 * launcher's libraries, Kotlin's among them, stands in for the host's own.
 *
 * @param launcherExecutable What the host starts to restart through the launcher; null when the
 * launcher cannot tell its own executable.
 * @param writesOutputToLog Whether the host's standard output and error go to its version's log
 * file; a `--headless` host keeps the launcher's.
 */
class InProcessHost(
    private val hostDirectory: HostDirectory,
    private val platformKey: String,
    private val launcherExecutable: String?,
    private val writesOutputToLog: Boolean,
) {
    /**
     * Sets the system properties [chosenHostJar]'s metadata and the launcher contract give the host,
     * then calls its main with [arguments]. Returns when that main returns, and throws what it throws.
     *
     * @param setAsideVersion A version this launch set aside, which the host tells the user about.
     */
    fun run(chosenHostJar: ChosenHostJar, setAsideVersion: HostVersion?, arguments: List<String>) {
        chosenHostJar.metadata.jvmArgsFor(platformKey).mapNotNull(::systemPropertyOf).forEach { (key, value) -> System.setProperty(key, value) }
        System.setProperty(LauncherContract.CONTRACT_PROPERTY, LauncherContract.VERSION.toString())
        launcherExecutable?.let { System.setProperty(LauncherContract.EXECUTABLE_PROPERTY, it) }
        System.setProperty(LauncherContract.HOST_DIRECTORY_PROPERTY, hostDirectory.root.toString())
        if (setAsideVersion == null) {
            System.clearProperty(LauncherContract.SET_ASIDE_VERSION_PROPERTY)
        } else {
            System.setProperty(LauncherContract.SET_ASIDE_VERSION_PROPERTY, setAsideVersion.name)
        }
        // The package sets skiko.library.path to its own app directory, which holds no skiko
        // library, and skiko then fails instead of extracting the one in the host's jar.
        System.clearProperty(SKIKO_LIBRARY_PATH_PROPERTY)

        if (writesOutputToLog) {
            Files.createDirectories(hostDirectory.logsDirectory)
            val output = PrintStream(FileOutputStream(hostDirectory.hostLogFile(chosenHostJar.version).toFile()), true)
            System.setOut(output)
            System.setErr(output)
        }

        val classLoader = URLClassLoader("jetwhale-host-${chosenHostJar.version.name}", arrayOf(chosenHostJar.path.toUri().toURL()), ClassLoader.getPlatformClassLoader())
        Thread.currentThread().contextClassLoader = classLoader
        val main = classLoader.loadClass(chosenHostJar.metadata.mainClass).getMethod("main", Array<String>::class.java)
        try {
            main.invoke(null, arguments.toTypedArray())
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}

private const val SKIKO_LIBRARY_PATH_PROPERTY = "skiko.library.path"
