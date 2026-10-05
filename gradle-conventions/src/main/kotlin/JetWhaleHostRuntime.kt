/**
 * What the host needs from the runtime that runs it. The host's release metadata states it, and the
 * launcher's runtime image is built from it, so a host never asks for more than the image it ships
 * with has.
 */
object JetWhaleHostRuntime {
    /** The host's class files target it: app-only dependencies such as aboutlibraries-core ship Java 21 bytecode. */
    const val JAVA_FEATURE_VERSION: Int = 21

    /**
     * Compose's default runtime modules, then the ones the host uses beyond them. A runtime image
     * without one shows the gap only when the packaged app runs, as a NoClassDefFoundError.
     */
    val modules: List<String> = listOf(
        "java.base",
        "java.desktop",
        "java.logging",
        "jdk.crypto.ec",
        "jdk.unsupported",
        "java.naming",
        "java.sql",
        "java.instrument",
    )

    /**
     * The JVM arguments the host asks for on every platform. The host runs in the launcher's JVM, and
     * the launcher sets each `-D` one as a system property before the host starts. An argument of any
     * other form also has to be in the launcher package's own `jvmArgs`: a launcher refuses a
     * downloaded version that asks for one its JVM did not start with.
     */
    val jvmArgs: List<String> = listOf("-Dcompose.application.configure.swing.globals=true")

    /** The platforms releases are built for, by `os-arch` key, with the JVM arguments each adds. */
    val platformJvmArgs: Map<String, List<String>> = mapOf(
        "macos-arm64" to listOf("-Dapple.awt.application.appearance=system"),
        "linux-x64" to emptyList(),
        "windows-x64" to emptyList(),
    )
}
