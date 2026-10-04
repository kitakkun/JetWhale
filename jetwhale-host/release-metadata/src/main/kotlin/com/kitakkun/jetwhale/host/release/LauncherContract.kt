package com.kitakkun.jetwhale.host.release

/**
 * What the launcher provides to the host it starts: the system properties it passes, the JVM
 * argument forms it accepts ([isAllowedHostJvmArgument]) and the restart protocol.
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

    /** The launcher's executable, which the host starts with [AFTER_ARGUMENT] to restart. */
    const val EXECUTABLE_PROPERTY = "jetwhale.launcher.executable"

    /** The [HostVersionsDirectory] the launcher chose from. */
    const val HOST_DIRECTORY_PROPERTY = "jetwhale.launcher.hostDir"

    /** A version this launch set aside because it failed its first starts. */
    const val SET_ASIDE_PROPERTY = "jetwhale.launcher.setAside"

    /**
     * `--after <pid>`: the launcher waits for that process to end before it chooses a version, so
     * that a host restarting into an update has freed its lock and its ports.
     */
    const val AFTER_ARGUMENT = "--after"

    /**
     * `--retry <version>`: the launcher clears that version's set-aside mark before it chooses, so
     * that the user's *Try again* runs it. The launcher alone writes `launcher-state.json`.
     */
    const val RETRY_ARGUMENT = "--retry"
}
