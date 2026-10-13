package com.kitakkun.jetwhale.host.release

/**
 * What the launcher provides to the host it runs in its own JVM: the system properties it sets before
 * the host's main, the JVM argument forms it accepts ([isAllowedHostJvmArgument]) and the restart
 * protocol.
 */
object LauncherContract {
    /**
     * The contract this build implements as a launcher and relies on as a host. It goes up when a
     * host starts relying on something an earlier launcher does not provide; a launcher refuses a
     * release whose [HostReleaseMetadata.launcherContract] is higher than its own.
     */
    const val VERSION = 1

    /** The contract the launcher implements. A host without it was not started by a launcher. */
    const val CONTRACT_PROPERTY = "jetwhale.launcher.contract"

    /**
     * The app's executable, which the host starts with [AFTER_ARGUMENT] to restart. On macOS the host
     * opens the app bundle around it through LaunchServices instead, so the new process is the app
     * rather than a child of the old one.
     */
    const val EXECUTABLE_PROPERTY = "jetwhale.launcher.executable"

    /** The [HostDirectory] the launcher chose from. */
    const val HOST_DIRECTORY_PROPERTY = "jetwhale.launcher.hostDir"

    /** A version this launch set aside because it failed its first starts. */
    const val SET_ASIDE_VERSION_PROPERTY = "jetwhale.launcher.setAsideVersion"

    /**
     * `--after <pid>`: the launcher waits for that process to end before it chooses a version, so
     * that a host restarting into an update has freed its lock and its ports. It waits before it
     * takes `launch.lock`, which a host ending within its startup time window takes to record that.
     */
    const val AFTER_ARGUMENT = "--after"

    /**
     * `--retry <version>`: the launcher clears that version's set-aside mark before it chooses, so
     * that the user's *Try again* runs it. The launcher alone writes `launcher-state.json`.
     */
    const val RETRY_ARGUMENT = "--retry"
}
