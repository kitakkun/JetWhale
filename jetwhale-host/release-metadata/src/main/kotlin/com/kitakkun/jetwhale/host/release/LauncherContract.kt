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
}
